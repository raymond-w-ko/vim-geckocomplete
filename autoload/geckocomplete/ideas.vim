let s:scratch_buf = v:false

function geckocomplete#ideas#init() abort
  if has("nvim")
    let s:scratch_buf = nvim_create_buf(v:false, v:true)
  endif
endfunction

function! s:close_nvim_floating_win()
  if exists("w:geckcomplete_floating_win")
    call nvim_win_close(w:geckcomplete_floating_win, v:true)
    unlet w:geckcomplete_floating_win
  endif
endfunction

function s:trigger_nvim_floating_win() abort
  if !has("nvim") | return | endif

  let [findstart, completions] = Geckocomplete_get_completions()
  let s:completions = completions
  let n = len(s:completions)
  if n == 0
    return
  endif

  let lines = []
  let max_line_length = 0
  for item in s:completions
    let abbr = item["abbr"]
    let m = len(abbr)
    if m > max_line_length
      let max_line_length = m
    endif
    call add(lines, abbr)
  endfor
  call nvim_buf_set_lines(s:scratch_buf, 0, -1, v:true, lines)

  let current_row = line(".")
  let current_col = col(".")
  let opts = {
      \ "style": "minimal",
      \ "focusable": v:false,
      \ "anchor": "NW",
      \ "width": max_line_length,
      \ "height": n,
      \ "relative": "cursor",
      \ "row": 1,
      \ "col": findstart - current_col + 1,
      \ }
  let win = nvim_open_win(s:scratch_buf, 0, opts)
  call nvim_win_set_option(win, "winhl", "Normal:Pmenu")
  let w:geckcomplete_floating_win = win
  autocmd CursorMoved,CursorMovedI,TabLeave,WinLeave,InsertLeave
      \ <buffer> ++once call s:close_nvim_floating_win()
endfunction

