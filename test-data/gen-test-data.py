import getopt
import os
import sys

def usage(argv, msg=None):
    if msg:
        print >>sys.stderr, msg
        print >>sys.stderr
    print >>sys.stderr, """\
Usage: %s [--dir <input_dir> | --file <input_file>]

Required options
^^^^^^^^^^^^^^^^
--active      The number of active enrollees to generate (default 3,000)
--inactive    The number of inactive enrollees to generate (default 7,000)

Standard options
^^^^^^^^^^^^^^^^
-h, --help  show this help and exit
""" % (sys.argv[0])

def main(argv):
    input_active = 3000
    input_inactive = 7000

    try:
        opts, args = getopt.getopt(argv,"ha:i",["active=","inactive=","help"])
    except getopt.GetoptError:
        usage(argv)
        sys.exit(2)

    for opt, arg in opts:
        if opt in ('-h', "--help"):
            usage(argv)
            sys.exit()
        elif opt in ("-a", "--active"):
            input_active = arg
        elif opt in ("-i", "--inactive"):
            input_inactive = arg

# Schema
# id | phone number | EDD | slot_start_day | fresh_base_day | fresh_base_slot | day | slot | status
#  Slot Start Day: is the day of the week that is "Day 1" for the user
#  Day: Either 1, 2 or 3 and it is the offset from their Slot Start Day. (Need to think about this and handle edge case of week end crossover)
#  Slot: either 1r, 2r, 3r, 1m, 2m, 3m
#  fresh_base_day: The default day that the user starts the week with
#  fresh_base_slot: The default slot that the user starts the week with
#  Status: Active, Inactive, Suspended, Cancelled, Completed

# select * from registrations where slot_start_day = <today> and day = 1 and slot = ? and status = 'Active'
# select * from registrations where slot_start_day = <yesterday> and day = 2 and slot = ? and status = 'Active'
# select * from registrations where slot_start_day = <two days ago> and day = 3 and slot = ? and status = 'Active'

if __name__ == "__main__":
    main(sys.argv[1:])
