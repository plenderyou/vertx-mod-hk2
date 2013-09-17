/*
 * The MIT License (MIT)
 * Copyright © 2013 Englishtown <opensource@englishtown.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the “Software”), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.englishtown.vertx.hk2;

import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.Binder;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.DeploymentVerticleFactory;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.impl.Deployment;
import org.vertx.java.platform.impl.java.CompilingClassLoader;
import org.vertx.java.platform.impl.java.JavaVerticleFactory;

/**
 * Extends the default vert.x {@link JavaVerticleFactory} using HK2 for dependency injection.
 */
public class HK2VerticleFactory extends JavaVerticleFactory implements DeploymentVerticleFactory {

    private Vertx vertx;
    private Container container;
    private ClassLoader cl;

    private static final Logger logger = LoggerFactory.getLogger(HK2VerticleFactory.class);

    private static final String CONFIG_BOOTSTRAP_BINDER_NAME = "hk2_binder";
    private static final String BOOTSTRAP_BINDER_NAME = "com.englishtown.vertx.hk2.BootstrapBinder";

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(Vertx vertx, Container container, ClassLoader cl) {
        super.init(vertx, container, cl);

        this.vertx = vertx;
        this.container = container;
        this.cl = cl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Verticle createVerticle(String main) throws Exception {
        return createVerticle(main, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Verticle createVerticle(Deployment deployment) throws Exception {
        return createVerticle(deployment.main, deployment.config);
    }

    protected Verticle createVerticle(String main, JsonObject config) throws Exception {
        String className = main;
        Class<?> clazz;

        if (isJavaSource(main)) {
            // TODO - is this right???
            // Don't we want one CompilingClassLoader per instance of this?
            CompilingClassLoader compilingLoader = new CompilingClassLoader(cl, main);
            className = compilingLoader.resolveMainClassName();
            clazz = compilingLoader.loadClass(className);
        } else {
            clazz = cl.loadClass(className);
        }
        Verticle verticle = createVerticle(clazz, config);
        verticle.setVertx(vertx);
        verticle.setContainer(container);
        return verticle;
    }

    private Verticle createVerticle(Class<?> clazz, JsonObject config) throws Exception {

        if (config == null) {
            config = new JsonObject();
        }
        String bootstrapName = config.getString(CONFIG_BOOTSTRAP_BINDER_NAME, BOOTSTRAP_BINDER_NAME);
        Binder bootstrap = null;

        try {
            Class bootstrapClass = cl.loadClass(bootstrapName);
            Object obj = bootstrapClass.newInstance();

            if (obj instanceof Binder) {
                bootstrap = (Binder) obj;
            } else {
                logger.error("Class " + bootstrapName
                        + " does not implement Binder.");
            }
        } catch (ClassNotFoundException e) {
            logger.warn("HK2 bootstrap binder class " + bootstrapName
                    + " was not found.  Are you missing injection bindings?");
        }

        // Each verticle factory will have it's own service locator instance
        ServiceLocatorFactory factory = ServiceLocatorFactory.getInstance();
        ServiceLocator locator = factory.create(null);

        bind(locator, new VertxBinder(this.vertx, this.container));
        if (bootstrap != null) {
            bind(locator, bootstrap);
        }

        return (Verticle) locator.createAndInitialize(clazz);
    }

    private boolean isJavaSource(String main) {
        return main.endsWith(".java");
    }

    private static void bind(ServiceLocator locator, Binder binder) {
        DynamicConfigurationService dcs = locator.getService(DynamicConfigurationService.class);
        DynamicConfiguration dc = dcs.createDynamicConfiguration();

        locator.inject(binder);
        binder.bind(dc);

        dc.commit();
    }
}
