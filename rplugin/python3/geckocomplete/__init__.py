from importlib.util import find_spec
import sys

from geckocomplete.geckocomplete import Geckocomplete

if find_spec("yarp"):
    import vim  # pylint: disable=import-error
else:
    import pynvim as vim

if hasattr(vim, "plugin"):

    @vim.plugin
    class GeckocompleteHandlers:
        def __init__(self, _vim):
            self._vim = _vim
            self._geckocomplete = Geckocomplete(self._vim)

        @vim.function("Geckocomplete_init", sync=True)
        def init(self, args):
            pass

        @vim.autocmd("BufReadPost,BufLeave,InsertEnter", pattern="*", sync=True)
        def sync_current_buffer(self):
            self._geckocomplete.sync_current_buffer()

        @vim.autocmd("BufDelete", pattern="*", sync=True)
        def delete_current_buffer(self):
            self._geckocomplete.delete_current_buffer()


if find_spec("yarp"):
    global_geckocomplete = Geckocomplete(vim)
