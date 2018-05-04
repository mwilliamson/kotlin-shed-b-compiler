function replace(old, replacement, string) {
    return string.split(old).join(replacement);
}

exports.replace = replace;
