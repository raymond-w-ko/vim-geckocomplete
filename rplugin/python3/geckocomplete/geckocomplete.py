import os
import socket
import json
import time
from geckocomplete.utils import (
    iskeyword_to_ords,
    iskeyword_to_ords_json,
    is_word_char,
    log,
)

DIR = os.path.dirname(os.path.realpath(__file__))
SOCKET_FILE = os.path.join(DIR, "../../../server/geckocomplete/geckocomplete.sock")
SOCKET_FILE = os.path.normpath(SOCKET_FILE)


class Geckocomplete:
    def __init__(self, vim):
        self.vim = vim
        self.last_complete_request = []

        log(DIR)
        log(SOCKET_FILE)
        self.socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            self.socket.connect(SOCKET_FILE)
        except socket.error as msg:
            log(str(msg))

    def to_server(self, cmd):
        s = json.dumps(cmd)
        self.socket.sendall(s.encode("utf-8"))

    def to_server_raw(self, bites):
        self.socket.sendall(bites)

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

    def is_buffer_modified(self, buf):
        return self.vim.eval("getbufinfo(%d)[0].changed" % (buf.number))

    def merge_current_buffer(self, event):
        buf = self.vim.current.buffer
        path = self.get_buf_path(buf)
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
        read_from_file = os.path.exists(path) and not modified
        if read_from_file:
            buffer_snapshot["num-chars"] = -1
            buffer_snapshot["num-bytes"] = -1
            buffer_snapshot["t"] = -1
            self.to_server(["merge-buffer", buffer_snapshot])
        else:
            text = self.nvim_copy_buffer(buf)
            bites = text.encode("utf-8")
            buffer_snapshot["num-chars"] = len(text)
            buffer_snapshot["num-bytes"] = len(bites)
            buffer_snapshot["t"] = time.time()
            self.to_server(["merge-buffer", buffer_snapshot])
            self.to_server_raw(bites)

    def delete_buffer(self, bufnum):
        # log("delete-buffer", bufnum)
        self.to_server(["delete-buffer", bufnum])

    def clear_last_complete_request(self):
        self.last_complete_request = []

    def get_completions(self):
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
        if self.last_complete_request == complete_request:
            return [-2, []]
        self.last_complete_request = complete_request

        log("completion:%d:%s:" % (findstart, word))
        self.to_server(["complete", word])
        completions = self.from_server()
        return [findstart, completions]
