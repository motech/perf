#!/usr/bin/python

import argparse
import time
import datetime
import random

# (id, creationDate,          creator, day, expectedDeliveryDate, status,      modificationDate,      modifiedBy, owner, phoneNumber,   slot)
# (1,  '2014-12-24 23:59:59', '',      '3', '2015-08-12',         'Cancelled', '2014-12-24 23:59:59', '',         '',    '8971536979', '2m'),
#
def make_line_printer (suffix): return lambda id, day, edd, status, phone, slot: \
    "({0}, '2014-12-24 23:59:59', '', '{1}', '{2}', '{3}', '2014-12-24 23:59:59', '', '', '{4}', '{5}'){6}\n"\
    .format(id, day, edd, status, phone, slot, suffix)

lpr_comma = make_line_printer(',')

lpr_semicolon = make_line_printer(';')

current_milli_time = lambda: int(round(time.time() * 1000))

random_phone = lambda: "{0:02d}{1:08d}".format(random.randrange(10,99), random.randrange(99999999))

statuses_def = {'Active': 1, 'Inactive': 3, 'Suspended': 3, 'Cancelled': 3, 'Completed': 3}
statuses = []
for k, v in statuses_def.iteritems():
    for i in range(v):
        statuses.append(k)
random.shuffle(statuses)
slots_def = {'1m': 7, '2m': 10, '3m': 7, '1r': 5, '2r': 5, '3r': 5}
slots = []
for k, v in slots_def.iteritems():
    for i in range(v):
        slots.append(k)
random.shuffle(slots)
days = ['1', '2', '3', '4', '5', '6', '7']


def write_recipients(start, f, count):
    id = start
    while id < count+start-1:
        slot = random.choice(slots)
        day = random.choice(days)
        edd = datetime.date.today() + datetime.timedelta(days=random.randrange(0,270))
        status = random.choice(statuses)
        phone = random_phone()

        f.write(lpr_comma(id, day, edd, status, phone, slot))
        id += 1
    slot = random.choice(slots)
    day = random.choice(days)
    edd = datetime.date.today() + datetime.timedelta(days=random.randrange(0,270))
    status = random.choice(statuses)
    phone = random_phone()
    f.write(lpr_semicolon(id, day, edd, status, phone, slot))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--output", help="output file name", default="recipients.sql")
    parser.add_argument("-c", "--count", help="number of recipients", default=1000, type=int)
    parser.add_argument("-s", "--start", help="start with id#", default=1, type=int)
    parser.add_argument("-t", "--truncate", help="truncate the recipients table", action="store_true")
    args = parser.parse_args()

    print "Generating {0:,d} recipient{1}...".format(args.count, '' if args.count == 1 else 's')
    f = open(args.output, 'w')
    if args.truncate:
        f.write('TRUNCATE TABLE KIL2_RECIPIENT;\n')
    f.write('LOCK TABLES KIL2_RECIPIENT WRITE;\nINSERT INTO KIL2_RECIPIENT (id,creationDate,creator,day,expectedDeliveryDate,status,modificationDate,modifiedBy,owner,phoneNumber,slot) VALUES\n')
    start = current_milli_time()
    write_recipients(args.start, f, args.count)
    millis = max(current_milli_time() - start, 1)
    rate = 1000.0 * args.count / millis
    print "{0:,d} recipient{1} generated in {2}ms ({3:,.2f}/s)".format(args.count, '' if args.count == 1 else 's', millis, rate)
    f.write('UNLOCK TABLES;\n')
