class Geckocomplete:
    def __init__(self, vim):
        self.vim = vim

    def get_buf_path(self, buf):
        return self.vim.eval("expand('#%d:p')" % (buf.number,))

    def sync_current_buffer(self):
        buf = self.vim.current.buffer
        iskeyword = self.vim.eval("&iskeyword")
        # self.vim.command("echom '%s'" % (iskeyword))
        # path = self.get_buf_path(buf)
        text = "\n".join(buf)
        # self.vim.command("echom '%d'" % (len(text)))

    def delete_current_buffer(self):
        buf = self.vim.current.buffer
