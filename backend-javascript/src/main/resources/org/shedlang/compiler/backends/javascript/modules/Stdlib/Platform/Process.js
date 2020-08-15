const Lists = require("../Lists");

async function args() {
    let args = Lists.nil;

    process.argv.slice(2).reverse().forEach(arg => {
        args = Lists.cons(arg, args);
    });

    return args;
}


exports.args = {async: args};
