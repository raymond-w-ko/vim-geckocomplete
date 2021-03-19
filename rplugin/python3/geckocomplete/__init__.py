from importlib.util import find_spec
import sys

from geckocomplete.geckocomplete import Geckocomplete
from geckocomplete.utils import log

if find_spec("yarp"):
    import vim  # pylint: disable=import-error
else:
    import pynvim as vim

is_nvim = hasattr(vim, "plugin")

if is_nvim:

    @vim.plugin
    class GeckocompleteHandlers:
        def __init__(self, _vim):
            self._vim = _vim
            self._geckocomplete = Geckocomplete(self._vim)

        @vim.function("Geckocomplete_init", sync=True)
        def init(self, _args):
            pass

        @vim.autocmd("BufReadPost", pattern="*", sync=True)
        def on_buf_read_post(self):
            self._geckocomplete.merge_current_buffer("BufReadPost")

        @vim.autocmd("BufLeave", pattern="*", sync=True)
        def on_buf_leave(self):
            self._geckocomplete.merge_current_buffer("BufLeave")

        @vim.autocmd("InsertEnter", pattern="*", sync=True)
        def on_insert_enter(self):
            self._geckocomplete.merge_current_buffer("InsertEnter")

        @vim.autocmd("InsertLeave", pattern="*", sync=True)
        def clear_last_complete_request(self):
            self._geckocomplete.clear_last_complete_request()

        @vim.function("Geckocomplete_delete_buffer", sync=True)
        def delete_buffer(self, args):
            bufnum = int(args[0])
            self._geckocomplete.delete_buffer(bufnum)

        @vim.function("Geckocomplete_get_completions", sync=True)
        def get_completions(self, _args):
            return self._geckocomplete.get_completions()


elif not is_nvim and find_spec("yarp"):
    global_geckocomplete = Geckocomplete(vim)

    def geckocomplete_init():
        pass

    def geckocomplete_merge_current_buffer(vim_event_name: str):
        return global_geckocomplete.merge_current_buffer(vim_event_name)

    def geckocomplete_clear_last_complete_request():
        return global_geckocomplete.clear_last_complete_request()

    def geckocomplete_delete_buffer(bufnum: int):
        return global_geckocomplete.delete_buffer(int(bufnum))

    def geckocomplete_get_completions():
        return global_geckocomplete.get_completions()
