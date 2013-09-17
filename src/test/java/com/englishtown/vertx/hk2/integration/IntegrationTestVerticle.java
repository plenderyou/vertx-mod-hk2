package com.englishtown.vertx.hk2.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

/**
 * Integration test to show a module deployed with a injection constructor
 */
@RunWith(CPJavaClassRunner.class)
public class IntegrationTestVerticle extends TestVerticle {

    @Test
    public void testDependencyInjection() throws Exception {

        JsonObject config = new JsonObject().putString("hk2_binder", "com.englishtown.vertx.hk2.MyBootstrapBinder");

        container.deployVerticle(DependencyInjectionVerticle.class.getName(), config, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> result) {
                assertTrue(result.succeeded());
                testComplete();
            }
        });

    }
}
