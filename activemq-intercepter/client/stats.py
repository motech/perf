from influxdb import client as influxdb
import json
from stompy.stomp import Stomp
import threading
import time

def listener():
    # Connect to activemq and subscribe to the stats queue
    stomp = Stomp("localhost")
    stomp.connect()
    stomp.subscribe({'destination':'/queue/stats', 'ack':'client'})

    # Connect to influxdb
    db = influxdb.InfluxDBClient(database="motech")

    while True:
        frame = stomp.receive_frame()
        print(frame.headers['message-id'])

        # Post to influxDB
        msg = json.loads(frame.body)

        if len(msg["subjects"]):
            data = []

            for subject in msg["subjects"]:
                data.append(
                    {
                        "name":"activemq_queue_depth_" + subject["subject"],
                        "columns":["timestamp", "value"],
                        "points":[[msg["timestamp"], subject["value"]]],
                    })

            print(data)

            try:
                db.write_points(data)
            except:
                db = influxdb.InfluxDBClient(database="motech")
                db.write_points(data)

        stomp.ack(frame)
# [
#   {
#     "name": "activemq_queue_depth",
#     "columns": ["time", "subject", "value"],
#     "points": [
#         [1400425947368, "", ]
#     ]
#   }
# ]

if __name__ == "__main__":
    stomp = Stomp("localhost")
    stomp.connect()

    t = threading.Thread(target=listener)
    t.daemon = True
    t.start()

    while True:
        time.sleep(1)

        # Send message to activemq
        stomp.send({'destination': '/queue/foo.bar',
                    'body': 'Testing',
                    'reply-to': '/queue/stats'})
