#!/usr/bin/python

import argparse
import time

def make_line_printer (suffix): return lambda id, day, edd, active, phone, slot: \
    "({0}, '2014-12-24 23:59:59', '', '{1}', '{2}', '\{3}', '2014-12-24 23:59:59', '', '', '{4}', '{5}'){6}\n"\
    .format(id, day, edd, active, phone, slot, suffix)

lpr_comma = make_line_printer(',')
lpr_semicolon = make_line_printer(';')

current_milli_time = lambda: int(round(time.time() * 1000))

def write_recipients(f, count):
    id = 1
    day = '1'
    edd='2015/09/08'
    active='1'
    phone='206 555 1212'
    slot='1m'
    while id < count:
        f.write(lpr_comma(id, day, edd, active, phone, slot))
        id = id + 1
    f.write(lpr_semicolon(id, day, edd, active, phone, slot))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--output", help="output file name",
                        default="recipients.sql")
    parser.add_argument("-c", "--count", help="number of recipients",
                        default=1000, type=int)
    args = parser.parse_args()

    print "Generating {0:,d} recipient{1}...".format(args.count, '' if args.count == 1 else 's')
    f = open(args.output, 'w')
    f.write('TRUNCATE TABLE KIL2_RECIPIENT;\nLOCK TABLES KIL2_RECIPIENT WRITE;\nINSERT INTO KIL2_RECIPIENT VALUES\n')
    start = current_milli_time()
    write_recipients(f, args.count)
    millis = max(current_milli_time() - start, 1)
    rate = 1000.0 * args.count / millis
    print "{0:,d} recipient{1} generated in {2}ms ({3:,.2f}/s)".format(args.count, '' if args.count == 1 else 's', millis, rate)
    f.write('UNLOCK TABLES;\n')
