let g:geckocomplete_quick_select_keys =
    \ get(g:, "geckocomplete_quick_select_keys", "1234567890")

let g:geckocomplete_completion_delay =
    \ get(g:, "geckocomplete_completion_delay", 350)

augroup geckocomplete
  au!
  autocmd CursorMovedI * call geckocomplete#completion_done()
  " autocmd InsertEnter *  call geckocomplete#completion_timer_start(0)
  autocmd TextChangedI * call geckocomplete#completion_timer_start(1)
  autocmd TextChangedP *  call geckocomplete#completion_timer_start(1)
  autocmd CompleteDone * call geckocomplete#completion_done()

  autocmd BufDelete * call geckocomplete#delete_current_buffer()
  autocmd BufEnter * call geckocomplete#setup_pmenu_highlight()
augroup END

inoremap <silent><nowait> <plug>(geckocomplete) <c-x><c-u>

let n = strlen(g:geckocomplete_quick_select_keys) - 1
for i in range(0, n, 1)
  let key = g:geckocomplete_quick_select_keys[i]
  let cmd = printf(
      \ "inoremap <silent><nowait> %s \<C-r>=geckocomplete#quick_select('%s', %d)\<CR>",
      \ key, key, i+1)
  exe cmd
endfor

function geckocomplete#setup_pmenu_highlight() abort
  hi Pmenu
      \ guifg=#00ff00 guibg=#003300 gui=none
      \ ctermbg=22 ctermfg=46 term=none cterm=none
  hi PmenuSel 
      \ guifg=#003300 guibg=#00ff00 gui=none
      \ ctermbg=46 ctermfg=22 term=none cterm=none
endfunction

call geckocomplete#init()

""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
" VIM 8+
""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
if has("nvim")
  finish
endif

let s:python_client = yarp#py3("geckocomplete")

func! Geckocomplete_delete_buffer(bufnum) abort
  return s:python_client.call("geckocomplete_delete_buffer", a:bufnum)
endfunc

func! Geckocomplete_get_completions() abort
  return s:python_client.call("geckocomplete_get_completions")
endfunc

func! Geckocomplete_merge_current_buffer(vim_event_type) abort
  return s:python_client.call("geckocomplete_merge_current_buffer", a:vim_event_type)
endfunc


augroup geckocomplete
  autocmd BufReadPost * call Geckocomplete_merge_current_buffer("BufReadPost")
  autocmd BufLeave * call Geckocomplete_merge_current_buffer("BufLeave")
  autocmd InsertEnter * call Geckocomplete_merge_current_buffer("InsertEnter")
augroup END
