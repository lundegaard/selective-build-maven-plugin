/*
 * Copyright (C) Lundegaard a.s. 2018 - All Rights Reserved
 *
 * Proprietary and confidential. Unauthorized copying of this file, via any
 * medium is strictly prohibited.
 */
package eu.lundegaard.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;

/**
 * @author Ales Rybak(ales.rybak@lundegaard.eu)
 */
@Mojo(
        name = "hello",
        defaultPhase = GENERATE_RESOURCES)
public class HelloMojo extends AbstractMojo {

    private static final Logger LOG = LoggerFactory.getLogger(HelloMojo.class);

    @Parameter(property = "hello", defaultValue = "Bazinga!")
    private String hello;

    @Override
    public void execute() {
        LOG.info("Say: {}", hello);
    }

}
