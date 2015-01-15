#!/usr/bin/python

import argparse
import datetime
import random
import sys

def make_line_printer (suffix): return lambda id, day, slot, phone, language, edd: \
    "('2014-12-24 23:59:59', '2014-12-24 23:59:59', '', '', '', {}, '{}', '{}', '{}', '{}', 'FB', '{}', '{}', '{}'){}\n"\
    .format(id, day, day, slot, slot, phone, language, edd, suffix)

lpr_comma = make_line_printer(',')

lpr_semicolon = make_line_printer(';')

rand_phone = lambda: "{0:02d}{1:08d}".format(random.randrange(10, 99), random.randrange(99999999))

rand_edd = lambda: "{:%Y%m%d}".format(datetime.date.today() + datetime.timedelta(days=random.randrange(0,270)))

languages = []
for i in range(50):
    languages.append("{}{}".format(chr(random.randrange(ord('a'),ord('z'))), chr(random.randrange(ord('a'),ord('z')))))
rand_language = lambda: random.choice(languages)

slots_def = {'1': 938, '2': 7010, '3': 876, '4': 561, '5': 420, '6': 195}
slots = []
for k, v in slots_def.iteritems():
    for i in range(v):
        slots.append(k)
random.shuffle(slots)
days = []


def write_recipients(start, f):
    id = start
    last = start + len(days) * len(slots) -1
    random.shuffle(days)
    for day in days:
        random.shuffle(slots)
        for slot in slots:
            if id < last:
                f.write(lpr_comma(id, day, slot, rand_phone(), rand_language(), rand_edd()))
            else:
                f.write(lpr_semicolon(id, day, slot, rand_phone(), rand_language(), rand_edd()))
            id += 1
    return id


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--output", help="output file name")
    parser.add_argument("-x", "--multiplier", help="number of times to multiply the base load", default=1, type=int)
    parser.add_argument("-d", "--days", help="number of days to populate", default=7, type=int)
    parser.add_argument("-s", "--start", help="start with id#", default=1, type=int)
    parser.add_argument("-t", "--truncate", help="truncate the recipients table", action="store_true")
    args = parser.parse_args()

    for i in range(1, args.days+1):
        days.append("{0:d}".format(i))
    if (args.output):
        f = open(args.output, 'w')
    else:
        f = sys.stdout
    if args.truncate:
        f.write('TRUNCATE TABLE KIL3_RECIPIENT;\n')
    id = args.start
    for i in range(args.multiplier):
        f.write('LOCK TABLES KIL3_RECIPIENT WRITE;\n')
        f.write('INSERT INTO KIL3_RECIPIENT (creationDate,modificationDate,creator,owner,modifiedBy,id,initialDay,day,initialSlot,slot,callStage,phone,language,expectedDeliveryDate) VALUES\n')
        id = write_recipients(id, f)
        f.write('UNLOCK TABLES;\n')
