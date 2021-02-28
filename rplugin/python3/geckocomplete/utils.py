import functools
import re
from datetime import datetime


def log(*args):
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
        if tok == "@":
            continue
        if "-" not in tok or tok == "-":
            valid_ords[ord(tok)] = True
        else:
            _range = tok.split("-")
            a = int(_range[0])
            b = int(_range[1])
            for i in range(a, b+1):
                valid_ords[i] = True
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
