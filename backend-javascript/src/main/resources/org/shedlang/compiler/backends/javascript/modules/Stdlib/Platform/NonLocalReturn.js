function nonLocalReturn(value) {
    throw {nonLocalReturn: true, value: value};
}

function run(func, onNonLocalReturn) {
    try {
        return func();
    } catch (error) {
        if (error.nonLocalReturn) {
            return onNonLocalReturn(error.value);
        } else {
            throw error;
        }
    }
}

exports.nonLocalReturn = nonLocalReturn;
exports.run = run;
