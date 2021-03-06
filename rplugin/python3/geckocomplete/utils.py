import functools
import re
from datetime import datetime


LOGGING_ENABLED = False


def log(*args):
    if not LOGGING_ENABLED:
        return
    t = datetime.now().isoformat()
    with open("/tmp/geckocomplete.log", "a") as f:
        args = map(str, args)
        line = " ".join(args)
        f.write("[%s] %s\n" % (t, line))


@functools.cache
def iskeyword_to_ords(iskeyword):
    tokens = iskeyword.split(",")
    valid_ords = {}
    for tok in tokens:
        should_include = True
        if len(tok) > 1 and tok.startswith("^"):
            should_include = False
            tok = tok[1:]
        if tok == "@":
            continue
        if "-" not in tok or tok == "-":
            valid_ords[ord(tok)] = should_include
        elif tok == "":
            # artifact of parsing a comma
            comma_ord = ord(",")
            valid_ords[comma_ord] = should_include
        else:
            _range = tok.split("-")

            a = _range[0]
            if a.isdigit():
                a = int(a)
            else:
                a = ord(a)

            b = _range[1]
            if b.isdigit():
                b = int(b)
            else:
                b = ord(b)

            for i in range(a, b+1):
                valid_ords[i] = should_include
    return valid_ords


@functools.cache
def iskeyword_to_ords_json(iskeyword):
    m = iskeyword_to_ords(iskeyword)
    ords = []
    for o, _ in m.items():
        ords.append(o)
    ords.sort()
    return ords


@functools.cache
def is_ch_in_regex_word_set(ch):
    m = re.match(r"\w", ch)
    return m is not None


def is_word_char(ords, ch):
    x = ord(ch)
    if x in ords:
        return True
    return is_ch_in_regex_word_set(ch)
