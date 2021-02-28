let g:geckocomplete_quick_select_keys =
    \ get(g:, "geckocomplete_quick_select_keys", "1234567890")
    
augroup geckocomplete
  autocmd CursorMovedI * call geckocomplete#completion_timer_start()
  autocmd InsertEnter *  call geckocomplete#completion_timer_start()
  autocmd BufDelete * call geckocomplete#delete_current_buffer()
  " autocmd TextChangedI * call geckocomplete#completion_timer_start()
  " autocmd TextChangedP *  call geckocomplete#completion_timer_start()
augroup END

noremap  <silent> <Plug>geckocomplete_trigger <nop>
inoremap <silent> <Plug>geckocomplete_trigger <c-x><c-u><c-p>

let n = strlen(g:geckocomplete_quick_select_keys) - 1
for i in range(0, n, 1)
  let key = g:geckocomplete_quick_select_keys[i]
  let cmd = printf(
      \ "inoremap <silent> %s \<C-r>=geckocomplete#quick_select('%s', %d)\<CR>",
      \ key, key, i+1)
  exe cmd
endfor

hi Pmenu
    \ guifg=#00ff00 guibg=#003300 gui=none
    \ ctermbg=22 ctermfg=46 term=none cterm=none
hi PmenuSel 
    \ guifg=#003300 guibg=#00ff00 gui=none 
    \ ctermbg=46 ctermfg=22 term=none cterm=none
