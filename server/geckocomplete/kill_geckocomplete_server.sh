#!/bin/bash
ps auxww | grep geckocomplete.jar | grep -v grep | awk '{printf $2}' | xargs kill
rm -f geckocomplete.sock
