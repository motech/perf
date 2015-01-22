#!/bin/bash

from flask import Flask, render_template, request
from os import remove, rename, chmod
import re
import csv
import random
import time
import urllib
import argparse


cdr_url_template = "{}/module/kil3/cdr/{}"
call_status_types = {'OK': 159, 'NA': 106, 'SO': 717, 'ND': 16}
call_status_choices = []
for key, value in call_status_types.iteritems():
    for i in range(value):
        call_status_choices.append(key)
pattern = re.compile('^day[1-7]-calls\.csv$')
app = Flask(__name__)

now = lambda: int(round(time.time() * 1000))
cdr_file_name = lambda day: "{}/day{}-cdrs.csv".format(args.cdr_dir, day)
tmp_cdr_file_name = lambda day: "{}~".format(cdr_file_name(day))
call_file_name = lambda day: "{}/day{}-calls.csv".format(args.call_dir, day)


parser = argparse.ArgumentParser()
parser.add_argument("-s", "--server", help="url of the Motech server",
                    default="http://localhost:8080/motech-platform-server")
parser.add_argument("-d", "--cdr_dir", help="cdr directory", default="/xfer/cdr")
parser.add_argument("-l", "--call_dir", help="call directory", default="/xfer/call")
args = parser.parse_args()


def call(day):
    line = 0
    cdr = 0
    with open(call_file_name(day), 'rb') as call_file, open(tmp_cdr_file_name(day), 'w') as cdr_file:
        reader = csv.reader(call_file, delimiter=',', skipinitialspace=True)
        for row in reader:
            line += 1
            if len(row) != 4:
                print "invalid line: {}".format(line)
                continue
            cdr_file.write("{},{},{}\n".format(row[0], row[1], random.choice(call_status_choices)))
            cdr += 1

    try:
        remove(cdr_file_name(day))
    except OSError as e:
        print("Error deleting old cdr file {}: {}".format(cdr_file_name(day), e.strerror))

    try:
        rename(tmp_cdr_file_name(day), cdr_file_name(day))
    except OSError as e:
        print("Error renaming {} to {}: {}".format(tmp_cdr_file_name(day), cdr_file_name(day), e.strerror))

    try:
        chmod(cdr_file_name(day), 0664)
    except OSError as e:
        print("Error setting permissions on {}: {}".format(cdr_file_name(day), e.strerror))

    return cdr


@app.route('/call')
def home():
    errors = []
    if 'day' not in request.args:
        errors.append("missing 'to' argument")
    if len(errors) > 0:
        return render_template('400.html', errors=errors), 400

    day = request.args['day']
    start = now()
    count = call(day)
    duration = now() - start
    message = ""
    if count > 0:
        message = "called {} ".format(count)
        message += ("cdr" if count == 1 else "cdrs")
        message += " at "
        if duration == 0:
            message += "lightspeed"
        else:
            message += "{} cdr{}/sec".format(1000 * count / duration, ("" if count == 1 else "s"))
    else:
        message = "no calls were made"

    urllib.urlopen(cdr_url_template.format(args.server, day)).read()

    return render_template('call.html', message=message, day=day)

if __name__ == "__main__":

    app.run(host='0.0.0.0')
