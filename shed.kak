hook global BufCreate .*[.]shed %{
  set-option buffer filetype shed
}

addhl shared/shed regions
addhl shared/shed/code default-region group
addhl shared/shed/comment-line  region     '//'  '$'             fill comment
addhl shared/shed/double-string region     '"' (?<!\\)(\\\\)*" fill string
addhl shared/shed/code/ regex '\b(?:as|effect|else|exit|export|extends|from|fun|Fun|handle|if|import|is|module|not|on|partial|resume|return|shape|tailrec|type|union|unit|val|varargs|when)\b' 0:keyword
addhl shared/shed/code/bool regex '\b(?:true|false)\b'                                0:keyword

hook -group shed-highlight global WinSetOption filetype=shed %{ add-highlighter window/ ref shed }
hook -group shed-highlight global WinSetOption filetype=(?!shed).* %{ remove-highlighter window/shed }

