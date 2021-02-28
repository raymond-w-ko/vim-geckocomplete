let s:completions = []

function geckocomplete#completefunc(findstart, base) abort
  if a:findstart
    let x = Geckocomplete_get_completions()
    let [i, completions] = x
    let s:completions = completions
    return i
  else
    return {
        \ "words": s:completions,
        \ "refresh": "always",
        \ }
  endif
endfunction

" no neovim support
" setlocal completeopt+=popup
" setlocal completepopup=height:10,width:60,align:item,border:on
function s:completion_begin() abort
  " due to the various plugins overriding these settings, always bash this
  if !exists("b:geckocomplete_buffer_setup")
    setlocal completefunc=geckocomplete#completefunc
    setlocal completeopt-=longest
    setlocal completeopt+=menuone
    setlocal completeopt-=menu
    setlocal completeopt+=noselect
    let b:geckocomplete_buffer_setup = 1
  endif
  call feedkeys("\<Plug>geckocomplete_trigger")
endfunction

""""""""""""""""""""""""""""""""""""""""

function! s:completion_timer_stop() abort
  if !exists("s:completion_timer")
    return
  endif
  call timer_stop(s:completion_timer)
  unlet s:completion_timer
endfunction

function! geckocomplete#completion_timer_start() abort
  if exists("s:completion_timer")
    call s:completion_timer_stop()
  endif

  let delay = 0
  if delay > 0
    let s:completion_timer = timer_start(delay, {-> s:completion_begin()})
  else
    call s:completion_begin()
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
