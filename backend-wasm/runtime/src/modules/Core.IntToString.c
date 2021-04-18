#include "shed.h"

void reverse(uint8_t* string, int length) {
    int left_index = 0;
    int right_index = length - 1;

    while (left_index < right_index) {
        uint8_t temp = string[left_index];
        string[left_index] = string[right_index];
        string[right_index] = temp;
        left_index++;
        right_index--;
    }
}

int itoa(uint8_t* string, int value) {
    if (value == 0) {
        string[0] = '0';
        return 1;
    } else if (value < 0) {
        string[0] = '-';
        return itoa(&string[1], -value) + 1;
    } else {
        int index = 0;
        while (value != 0) {
            string[index++] = '0' + (value % 10);
            value = value / 10;
        }

        reverse(string, index);

        return index;
    }
}

ShedAny shed_module_fun__Core__IntToString__intToString(void* environment, ShedAny value) {
    ShedString string = shed_malloc(4 + 11, 4);
    string->length = itoa(string->data, value);
    return (ShedAny)string;
}
