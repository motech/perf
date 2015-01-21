#!/usr/bin/python

import argparse
import datetime
import random
import sys

def make_line_printer (suffix): return lambda day, status, phone, language, edd: \
    "('2014-12-24 23:59:59', '2014-12-24 23:59:59', '', '', '', '{}', '{}', '{}', 'FB', '{}', '{}', '{}'){}\n"\
    .format(day, day, status, phone, language, edd, suffix)

lpr_comma = make_line_printer(',')

lpr_semicolon = make_line_printer(';')

rand_phone = lambda: "{0:02d}{1:08d}".format(random.randrange(10, 99), random.randrange(99999999))

rand_status = lambda count: "IN" if (count+1) % 10 == 0 else "AC"

rand_edd = lambda: "{:%Y%m%d}".format(datetime.date.today() + datetime.timedelta(days=random.randrange(0,270)))

languages = []
for i in range(50):
    languages.append("{}{}".format(chr(random.randrange(ord('a'),ord('z'))), chr(random.randrange(ord('a'),ord('z')))))
rand_language = lambda: random.choice(languages)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("day")
    parser.add_argument("count", type=int)
    parser.add_argument("-o", "--output", help="output file name")
    parser.add_argument("-t", "--truncate", help="truncate the recipients table", action="store_true")
    args = parser.parse_args()

    if (args.output):
        f = open(args.output, 'w')
    else:
        f = sys.stdout

    if args.truncate:
        f.write('TRUNCATE TABLE KIL3_RECIPIENT;\n')

    f.write('LOCK TABLES KIL3_RECIPIENT WRITE;\n')
    f.write('ALTER TABLE KIL3_RECIPIENT MODIFY COLUMN id INT auto_increment;\n')
    f.write('INSERT INTO KIL3_RECIPIENT (creationDate,modificationDate,creator,owner,modifiedBy,initialDay,day,status,callStage,phone,language,expectedDeliveryDate) VALUES\n')
    for i in range(args.count-1):
        f.write(lpr_comma(args.day, rand_status(i), rand_phone(), rand_language(), rand_edd()))
    f.write(lpr_semicolon(args.day, rand_status(i+1), rand_phone(), rand_language(), rand_edd()))
    f.write('ALTER TABLE KIL3_RECIPIENT MODIFY COLUMN id INT;\n')
    f.write('UNLOCK TABLES;\n')
