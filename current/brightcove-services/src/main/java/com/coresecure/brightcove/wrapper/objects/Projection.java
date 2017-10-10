package com.coresecure.brightcove.wrapper.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Projection {

    public String type;

    private static final Logger LOGGER = LoggerFactory.getLogger(Projection.class);

    public Projection(String aType)
    {

        type = aType;

    }


    public String toString()
    {
        return type;
    }

}
