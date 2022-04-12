let s:pause_completion = 0
let s:pmenu_first_time = 0
let s:use_existing_completions = 0
let s:findstart = -3
let s:completions = []

function geckocomplete#init() abort
endfunction

function geckocomplete#completefunc(findstart, base) abort
  if a:findstart
    " do below to avoid immediate recompletion after BS, only after a delay
    " if !s:pmenu_first_time && g:geckocomplete_completion_delay > 0
    "   call geckocomplete#completion_timer_start(0)
    "   return -2
    " else
    "   let s:pmenu_first_time = 0
    " endif

    if s:use_existing_completions
      let s:use_existing_completions = 0
      return s:findstart
    else
      let x = Geckocomplete_get_completions()
      let [findstart, completions] = x
      let s:completions = completions
      return findstart
    endif
  else
    return {
        \ "words": s:completions,
        \ "refresh": "always",
        \ }
  endif
endfunction

function s:trigger_pmenu() abort
  if mode()[0] != 'i' | return | endif

  " always bash this, plugins like clojure-vim/clojure.vim sets this
  setlocal completefunc=geckocomplete#completefunc

  " let s:pmenu_first_time = 1

  " due to the various plugins overriding these settings, always bash this
  if !exists("b:geckocomplete_buffer_setup")
    setlocal completeopt-=longest
    setlocal completeopt+=menuone
    setlocal completeopt-=menu
    setlocal completeopt+=noselect
    setlocal completeopt+=noinsert
    let b:geckocomplete_buffer_setup = 1
  endif

    let x = Geckocomplete_get_completions()
    let [findstart, completions] = x
    if len(completions) > 0
      let s:findstart = findstart
      let s:completions = completions
      let s:use_existing_completions = 1
      call feedkeys("\<plug>(geckocomplete)", "n")
    endif

  " let [findstart, completions] = Geckocomplete_get_completions()
  " call complete(findstart + 1, completions)
endfunction

" no neovim support
" setlocal completeopt+=popup
" setlocal completepopup=height:10,width:60,align:item,border:on
function s:completion_begin() abort
  call s:completion_timer_stop()
  if &paste | return | endif
  if has_key(g:geckocomplete_disable_filetype, &ft) | return | endif
  call s:trigger_pmenu()
endfunction


""""""""""""""""""""""""""""""""""""""""

function! s:completion_timer_stop() abort
  if !exists("s:completion_timer")
    return
  endif
  call timer_stop(s:completion_timer)
  unlet s:completion_timer
endfunction

function! geckocomplete#completion_timer_start(show_now) abort
  call s:completion_timer_stop()
  let delay = g:geckocomplete_completion_delay

  if s:pause_completion | return | endif

  if !a:show_now && delay > 0
    let s:completion_timer = timer_start(delay, {-> s:completion_begin()})
  else
    call s:completion_begin()
  endif
endfunction

function geckocomplete#toggle_pause_completion()
  if s:pause_completion
    let s:pause_completion = 0
  else
    let s:pause_completion = 1
  endif
  if pumvisible()
    return "\<C-e>"
  else
    return ""
  endif
endfunction

fun! geckocomplete#unpause_completion() abort
  let s:pause_completion = 0
endf

" The buffer number must be retrieved via VimScript, otherwise it is too late
" to do it in the plugin.
function! geckocomplete#delete_current_buffer() abort
  let bufnum = expand("<abuf>")
  call Geckocomplete_delete_buffer(bufnum)
endfunction

""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
" public functions
""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
function! geckocomplete#completion_key() abort
  if pumvisible()
    return "\<C-n>\<C-y>"
  else
    return "\<Tab>"
  endif
endfunction

fun! s:resume_completion_soon() abort
  if exists("s:resume_completion_timer")
    call timer_stop(s:resume_completion_timer)
  endif
  let s:resume_completion_timer = timer_start(1000, {-> geckocomplete#unpause_completion()})
endf

" assume these are numbers
function! geckocomplete#quick_select(key, index)
  if !pumvisible()
    let s:pause_completion = 1
    call s:resume_completion_soon()
    return a:key
  else
    let keys = ""
    for i in range(a:index)
      let keys .= "\<C-n>"
    endfor
    let keys .= "\<C-y>"
    return keys
  endif
endfunction

" vim: et ts=2 sts=2 sw=2
