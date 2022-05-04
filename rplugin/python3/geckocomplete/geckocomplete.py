import os
import socket
import json
import time
import threading
import platform
from queue import Queue
from subprocess import Popen, DEVNULL
from geckocomplete.utils import (
    iskeyword_to_ords,
    iskeyword_to_ords_json,
    is_word_char,
    log,
)

DIR = os.path.dirname(os.path.realpath(__file__))
DIR = os.path.normpath(DIR)
SERVER_DIR = os.path.join(DIR, "../../../server/geckocomplete")
SERVER_DIR = os.path.normpath(SERVER_DIR)
SOCKET_FILE = os.path.join(DIR, "../../../server/geckocomplete/geckocomplete.sock")
SOCKET_FILE = os.path.normpath(SOCKET_FILE)
IGNORED_FILE_TYPES = {"fzf", "startify"}


def start_server_process():
    cmd = ["java", "-jar", "geckocomplete.jar"]
    kwargs = {"cwd": SERVER_DIR}
    if platform.system() == "Windows":
        CREATE_NEW_PROCESS_GROUP = 0x00000200
        DETACHED_PROCESS = 0x00000008
        kwargs.update(creationflags=DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP)
    else:
        kwargs.update(start_new_session=True)
    Popen(cmd, stdin=DEVNULL, stdout=DEVNULL, stderr=DEVNULL, **kwargs)


class Geckocomplete:
    def __init__(self, vim):
        self.vim = vim
        self.last_complete_request = []
        self.ready = False
        self.broken = False
        self.started_server = False
        self.to_server_q = Queue()
        self.socket = None
        t = threading.Thread(target=self.send_to_server_loop, daemon=True)
        t.start()

    def is_nvim(self):
        return hasattr(self.vim, "plugin")

    def send_to_server_loop(self):
        "This runs in a separate daemon thread"
        while True:
            if not os.path.exists(SOCKET_FILE):
                if not self.started_server:
                    start_server_process()
                    self.started_server = True
                time.sleep(4)
                continue
            if not self.ready:
                log(SOCKET_FILE)
                self.socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                self.socket.connect(SOCKET_FILE)
                self.socket.settimeout(2.0)
                self.ready = True
            payload = self.to_server_q.get()
            self.socket.sendall(payload)

    def to_server_int(self, n):
        bites = n.to_bytes(4, byteorder="big")
        self.to_server_q.put(bites)

    def to_server(self, cmd):
        s = json.dumps(cmd)
        bites = s.encode("utf-8")
        n = len(bites)
        self.to_server_int(n)
        self.to_server_q.put(bites)

    def to_server_raw(self, bites):
        self.to_server_q.put(bites)

    def _socket_read(self, n):
        chunks = []
        bytes_recd = 0
        while bytes_recd < n:
            chunk = self.socket.recv(min(n - bytes_recd, 1024))
            if chunk == b"":
                raise RuntimeError("socket connection broken")
            chunks.append(chunk)
            bytes_recd += len(chunk)
        return b"".join(chunks)

    def from_server(self):
        bites = self._socket_read(4)
        n = int.from_bytes(bites, "big")
        s = self._socket_read(n).decode("utf-8")
        return json.loads(s)

    def get_buf_path(self, buf):
        return self.vim.eval("expand('#%d:p')" % (buf.number,))

    def nvim_copy_buffer(self, buf):
        lua_code = """
    return table.concat(
        vim.api.nvim_buf_get_lines(..., 0, -1, true),
        "\\n"
    )"""
        x = self.vim.exec_lua(lua_code.strip(), buf.number)
        return x

    def vim_copy_buffer(self, buf):
        return "\n".join(buf)

    def is_buffer_modified(self, buf):
        return self.vim.eval("getbufinfo(%d)[0].changed" % (buf.number))

    def merge_current_buffer(self, event):
        buf = self.vim.current.buffer
        path = self.get_buf_path(buf)

        buflisted = buf.options["buflisted"]
        if not buflisted:
            return
        bufhidden = buf.options["bufhidden"]
        if bufhidden.strip():
            return
        filetype = buf.options["filetype"]
        if filetype in IGNORED_FILE_TYPES:
            return

        iskeyword = self.vim.eval("&iskeyword")
        ords = iskeyword_to_ords_json(iskeyword)

        buffer_snapshot = {
            "buffer-id": buf.number,
            "iskeyword-ords": ords,
            "buffer-path": path,
            "event": event,
        }

        # log("merge-buffer", str(buffer_snapshot))
        modified = self.is_buffer_modified(buf)
        read_from_file = (
            os.path.exists(path) and not os.path.isdir(path) and not modified
        )
        if read_from_file:
            buffer_snapshot["num-chars"] = -1
            buffer_snapshot["num-bytes"] = -1
            buffer_snapshot["t"] = -1
            buffer_snapshot["read-from-disk"] = True
            self.to_server(["merge-buffer", buffer_snapshot])
        else:
            if self.is_nvim():
                text = self.nvim_copy_buffer(buf)
            else:
                text = self.vim_copy_buffer(buf)
            bites = text.encode("utf-8")
            n_bites = len(bites)
            buffer_snapshot["num-chars"] = len(text)
            buffer_snapshot["num-bytes"] = n_bites
            buffer_snapshot["t"] = time.time()
            buffer_snapshot["read-from-disk"] = False
            self.to_server(["merge-buffer", buffer_snapshot])
            if n_bites > 0:
                log("sending num bytes: " + str(n_bites))
                self.to_server_raw(bites)

    def delete_buffer(self, bufnum):
        # log("delete-buffer", bufnum)
        self.to_server(["delete-buffer", bufnum])

    def clear_last_complete_request(self):
        self.last_complete_request = []

    def get_completions(self):
        if self.broken:
            return [-2, []]
        if not self.ready:
            return [-2, []]

        row, col = self.vim.current.window.cursor
        iskeyword = self.vim.eval("&iskeyword")
        ords = iskeyword_to_ords(iskeyword)
        line = self.vim.current.line
        line_prefix = line[:col]
        i = len(line_prefix) - 1

        # we may be at first column, or not even after a word
        if i < 0 or not is_word_char(ords, line_prefix[i]):
            return [-2, []]

        while i >= 0:
            ch = line_prefix[i]
            if not is_word_char(ords, ch):
                break
            i -= 1
        findstart = i + 1
        word = line_prefix[findstart:]

        buf = self.vim.current.buffer
        complete_request = [buf.number, row, findstart, word]
        # if self.last_complete_request == complete_request:
        #     return [-2, []]
        self.last_complete_request = complete_request

        # log("completion:%d:%s:" % (findstart, word))
        self.to_server(["complete", word])
        try:
            completions = self.from_server()
            return [findstart, completions]
        except socket.timeout:
            self.broken = True
            return [-2, []]
