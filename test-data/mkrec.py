#!/usr/bin/python

import argparse
import datetime
import random
import sys

# (id, creationDate,          creator, day, expectedDeliveryDate, status,      modificationDate,      modifiedBy, owner, phoneNumber,   slot)
# (1,  '2014-12-24 23:59:59', '',      '3', '2015-08-12',         'Cancelled', '2014-12-24 23:59:59', '',         '',    '8971536979', '2m'),
#
def make_line_printer (suffix): return lambda id, day, edd, status, phone, slot: \
    "({0}, '2014-12-24 23:59:59', '', '{1}', '{2}', '{3}', '2014-12-24 23:59:59', '', '', '{4}', '{5}'){6}\n"\
    .format(id, day, edd, status, phone, slot, suffix)

lpr_comma = make_line_printer(',')

lpr_semicolon = make_line_printer(';')

random_phone = lambda: "{0:02d}{1:08d}".format(random.randrange(10,99), random.randrange(99999999))

statuses_def = {'Active': 1, 'Inactive': 1, 'Suspended': 1, 'Cancelled': 1, 'Completed': 1}
statuses = []
for k, v in statuses_def.iteritems():
    for i in range(v):
        statuses.append(k)
random.shuffle(statuses)
slots_def = {'1m': 1114, '2m': 7430, '3m': 876, '1r': 561, '2r': 0, '3r': 19}
slots = []
for k, v in slots_def.iteritems():
    for i in range(v):
        slots.append(k)
random.shuffle(slots)
days = ['1', '2', '3', '4', '5', '6', '7']
base_load = len(days) * len(slots) * len(statuses)


def write_recipients(start, f):
    id = start
    last = start + base_load -1
    random.shuffle(days)
    for day in days:
        random.shuffle(slots)
        for slot in slots:
            random.shuffle(statuses)
            for status in statuses:
                edd = datetime.date.today() + datetime.timedelta(days=random.randrange(0,270))
                status = random.choice(statuses)
                phone = random_phone()
                if id < last:
                    f.write(lpr_comma(id, day, edd, status, phone, slot))
                else:
                    f.write(lpr_semicolon(id, day, edd, status, phone, slot))
                id += 1
    return id


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--output", help="output file name")
    parser.add_argument("-x", "--multiplier", help="number of times to multiply the base load ({0})".format(base_load), default=1, type=int)
    parser.add_argument("-s", "--start", help="start with id#", default=1, type=int)
    parser.add_argument("-t", "--truncate", help="truncate the recipients table", action="store_true")
    args = parser.parse_args()

    # print "Generating {0:,d} recipient{1}...".format(args.count, '' if args.count == 1 else 's')
    if (args.output):
        f = open(args.output, 'w')
    else:
        f = sys.stdout
    if args.truncate:
        f.write('TRUNCATE TABLE KIL2_RECIPIENT;\n')
    id = args.start
    for i in range(args.multiplier):
        f.write('LOCK TABLES KIL2_RECIPIENT WRITE;\nINSERT INTO KIL2_RECIPIENT (id,creationDate,creator,day,expectedDeliveryDate,status,modificationDate,modifiedBy,owner,phoneNumber,slot) VALUES\n')
        id = write_recipients(id, f)
        f.write('UNLOCK TABLES;\n')
