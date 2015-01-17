package org.motechproject.kil3.service;

import com.google.common.base.Strings;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

@Service("expectationService")
public class ExpectationServiceImpl implements ExpectationService {

    private final static String REDIS_SERVER_PROPERTY = "kil3.redis_server";
    private final static long MILLIS_PER_SECOND = 1000;
    private final static String REDIS_JOB_ID = "job_id";

    private Logger logger = LoggerFactory.getLogger(ExpectationServiceImpl.class);

    SettingsFacade settingsFacade;
    JedisPool jedisPool;



    @Autowired
    public ExpectationServiceImpl(@Qualifier("kil3Settings") SettingsFacade settingsFacade) {
        this.settingsFacade = settingsFacade;
        String redisServer = settingsFacade.getProperty(REDIS_SERVER_PROPERTY);
        logger.info("redis server: {}", redisServer);
        jedisPool = new JedisPool(new JedisPoolConfig(), redisServer);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(REDIS_JOB_ID, "0");
        }

    }


    private static String redisJobExpectations(String jobId) {
        return String.format("%s-expectations", jobId);
    }


    private static String redisJobExpecting(String jobId) {
        return String.format("%s-expecting", jobId);
    }


    private static String redisJobTimer(String jobId) {
        return String.format("%s-timer", jobId);
    }


    private static long redisTime(Jedis jedis) {
        List<String> t = jedis.time();
        return Long.valueOf(t.get(0)) * 1000 + Long.valueOf(t.get(1)) / 1000;
    }


    public void setExpectations(String jobId, long count) {
        logger.info("setExpectations({}, {})", jobId, count);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(redisJobExpectations(jobId), String.valueOf(count));
            jedis.set(redisJobExpecting(jobId), String.valueOf(count));
            jedis.del(redisJobTimer(jobId));
        }
    }


    public void meetExpectation(String jobId) {
        logger.debug("meetExpectation({})", jobId);

        try (Jedis jedis = jedisPool.getResource()) {

            // Start timer if not already started
            if (!jedis.exists(redisJobTimer(jobId))) {
                List<String> t = jedis.time();
                jedis.setnx(redisJobTimer(jobId), String.valueOf(redisTime(jedis)));
            }

            long expecting = jedis.decr(redisJobExpecting(jobId));

            // All expectations met
            if (expecting <= 0) {

                List<String> t = jedis.time();
                long milliStop = redisTime(jedis);
                long milliStart = Long.valueOf(jedis.get(redisJobTimer(jobId)));
                long millis = milliStop - milliStart;
                String expectationsString = jedis.get(redisJobExpectations(jobId));
                if (Strings.isNullOrEmpty(expectationsString)) {
                    logger.warn("meetExpectation was called on a null redis key: {}", redisJobExpectations(jobId));
                } else {
                    long expectations = Long.valueOf(expectationsString);
                    float rate = (float) expectations * MILLIS_PER_SECOND / millis;
                    logger.info("Measured {} calls at {} calls/second", expectations, rate);

                    jedis.del(redisJobExpectations(jobId));
                    jedis.del(redisJobExpecting(jobId));
                    jedis.del(redisJobTimer(jobId));
                }

            } else if (expecting % 1000 == 0) {

                long milliStop = redisTime(jedis);
                long milliStart = Long.valueOf(jedis.get(redisJobTimer(jobId)));
                long millis = milliStop - milliStart;
                long expectations = Long.valueOf(jedis.get(redisJobExpectations(jobId)));
                long count = expectations - expecting;
                float rate = (float) count * MILLIS_PER_SECOND / millis;
                logger.info(String.format("Expectations: %d/%d @ %f/s", expecting, expectations, rate));

            }
        }
    }


    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        try (Jedis jedis = jedisPool.getResource()) {
            for (String expectationsKey : jedis.keys("*-expectations")) {
                String jobId = expectationsKey.substring(0, expectationsKey.length() - "-expectations".length());
                sb.append(sep);
                sb.append(String.format("%s: %s/%s", jobId, jedis.get(redisJobExpectations(jobId)),
                        jedis.get(redisJobExpecting(jobId))));
                if (sep.isEmpty()) {
                    sep = "\n\r";
                }
            }
        }
        return sb.toString();
    }
}
