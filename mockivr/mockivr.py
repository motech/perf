import threading
from flask import Flask, render_template, request
import time
import Queue
import random
import logging

NUM_QUEUE = 1000
TIME_WARP = 100.0
QUEUE_LOG_LEVEL = logging.DEBUG
CALL_LOG_LEVEL = logging.WARNING
WEB_LOG_LEVEL = logging.WARNING

millis = lambda: str(int(round(time.time() * 1000)))

queues = []
call_number = 1
statuses = [
    {'name': 'success',       'likelihood': 40, 'min-duration': 100000, 'max-duration': 140000},
    {'name': 'no answer',     'likelihood': 30, 'min-duration':  10000, 'max-duration':  30000},
    {'name': 'off',           'likelihood': 15, 'min-duration':    500, 'max-duration':   2000},
    {'name': 'not delivered', 'likelihood': 15, 'min-duration':    500, 'max-duration':  10000},
]
status_likelihood = []
status_history = []
app = Flask(__name__)
queue_log = logging.getLogger('queue')
call_log = logging.getLogger('call')


@app.route('/')
def home():
    stats = dict()
    for i in len(queues):
        stats['queue {0} size'.format(i)] = queues[i].qsize()
    return render_template('index.html', stats=stats)


@app.route('/enqueue')
def enqueue():

    global call_number

    count = 1
    if request.args.has_key('count'):
        count = int(request.args['count'])

    for i in range(call_number, call_number + count):
        status_index = random.choice(status_likelihood)
        status_history[status_index] += 1
        queues[i % NUM_QUEUE].put({'call-id': i, 'status-index': status_index})

    call_number += count

    return "added {0} {1}, next call_number is {2}".format(count, 'call' if 1 == count else 'calls', call_number)


def call(call_info):

    status = statuses[call_info['status-index']]
    call_duration = random.randrange(status['min-duration'], status['max-duration'])
    time.sleep(call_duration / 1000.0 / TIME_WARP)

    call_log.info("{0}\t{1}\t{2}\t{3}".format(millis(), call_info['call-id'], status['name'], call_duration))


def queue_worker(qid):
    while True:
        call_info = queues[qid].get()
        call(call_info)
        queues[qid].task_done()


def print_queue_info(last_queue_size):
    size = 0
    for q in queues:
        size += q.qsize()

    if size != last_queue_size:
        queue_log.info("The combined queue size is {}".format(size))
        if queue_log.level <= logging.DEBUG:
            if size == 0 and last_queue_size > 0:
                total = 0
                for i in range(len(statuses)):
                    total += status_history[i]
                queue_log.debug("Total number of calls: {}".format(total))
                s = ''
                for i in range(len(statuses)):
                    percent = 100.0 * status_history[i] / total
                    s += "{0}{1}: {2}({3:.2f}%)".format(', ' if i > 0 else '', statuses[i]['name'], status_history[i],
                                                        percent)
                queue_log.debug(s)

    threading.Timer(0.5, print_queue_info, (size,)).start()


def setup_logging():
    logging.basicConfig(level=logging.INFO)
    web_log = logging.getLogger('werkzeug')
    web_log.setLevel(WEB_LOG_LEVEL)
    call_log.setLevel(CALL_LOG_LEVEL)
    queue_log.setLevel(QUEUE_LOG_LEVEL)


if __name__ == '__main__':

    setup_logging()

    for i in range(len(statuses)):
        status_history.append(0)
        for j in range(statuses[i]['likelihood']):
            status_likelihood.append(i)

    for i in range(NUM_QUEUE):
        queues.append(Queue.Queue())
        t = threading.Thread(target=queue_worker, args=(i,))
        t.daemon = True
        t.start()

    queue_log.info("created {0} queue {1}".format(NUM_QUEUE, 'workers' if NUM_QUEUE > 1 else 'worker'))

    print_queue_info(-1)

    app.run()
