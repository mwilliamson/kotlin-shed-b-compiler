function print(value) {
    process.stdout.write(value);
}

module.exports = {
    print: {async: print},
};
