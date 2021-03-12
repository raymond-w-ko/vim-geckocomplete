let s:pause_completion = 0
let s:pmenu_first_time = 0
let s:completions = []

function geckocomplete#init() abort
endfunction

function geckocomplete#completefunc(findstart, base) abort
  if a:findstart
    " do below to avoid immediate recompletion after BS, only after a delay
    if !s:pmenu_first_time && g:geckocomplete_completion_delay > 0
      echom "abort"
      call geckocomplete#completion_timer_start(0)
      return -1
    else
      let s:pmenu_first_time = 0
    endif

    let x = Geckocomplete_get_completions()
    let [findstart, completions] = x
    let s:completions = completions
    return findstart
  else
    return {
        \ "words": s:completions,
        \ }
  endif
endfunction

function s:trigger_pmenu() abort
  " due to the various plugins overriding these settings, always bash this
  if !exists("b:geckocomplete_buffer_setup")
    setlocal completefunc=geckocomplete#completefunc
    setlocal completeopt-=longest
    setlocal completeopt+=menuone
    setlocal completeopt-=menu
    setlocal completeopt+=noselect
    setlocal completeopt+=noinsert
    let b:geckocomplete_buffer_setup = 1
  endif
  let s:pmenu_first_time = 1
  call feedkeys("\<Plug>geckocomplete_trigger", "in")
endfunction

" no neovim support
" setlocal completeopt+=popup
" setlocal completepopup=height:10,width:60,align:item,border:on
function s:completion_begin() abort
  call s:completion_timer_stop()
  if &paste | return | endif
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

function! geckocomplete#completion_timer_start(delay_only) abort
  call s:completion_timer_stop()
  let delay = g:geckocomplete_completion_delay

  if s:pause_completion | return | endif
  if a:delay_only &&  delay <= 0 | return | endif

  if delay > 0
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

function! geckocomplete#quick_select(key, index)
  if !pumvisible()
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
