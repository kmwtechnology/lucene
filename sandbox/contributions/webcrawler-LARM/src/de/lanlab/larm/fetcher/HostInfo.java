/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Lucene" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Lucene", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package de.lanlab.larm.fetcher;

import java.util.HashMap;
import java.net.*;
import de.lanlab.larm.util.CachingQueue;
import de.lanlab.larm.util.Queue;

/**
 * contains information about a host. If a host doesn't respond too often, it's
 * excluded from the crawl.
 * This class is used by the HostManager
 *
 * @author    Clemens Marschner
 * @created   16. Februar 2002
 * @version $Id$
 */
public class HostInfo
{
    static final String[] emptyKeepOutDirectories = new String[0];

    int id;
    int healthyCount = 5;   // five strikes, and you're out
    boolean isReachable = true;
    boolean robotTxtChecked = false;
    String[] disallows;    // robot exclusion
    boolean isLoadingRobotsTxt = false;
    Queue queuedRequests = null; // robot exclusion
    String hostName;

    public HostInfo(String hostName, int id)
    {
        this.id = id;
        this.disallows = HostInfo.emptyKeepOutDirectories;
        this.hostName = hostName;
    }

    /**
     * is this host reachable and responding?
     */
    public boolean isHealthy()
    {
        return (healthyCount > 0) && isReachable;
    }

    /**
     * signals that the host returned with a bad request of whatever type
     */
    public void badRequest()
    {
        healthyCount--;
    }

    public void setReachable(boolean reachable)
    {
        isReachable = reachable;
    }

    public boolean isReachable()
    {
        return isReachable;
    }

    public boolean isRobotTxtChecked()
    {
        return robotTxtChecked;
    }

    /**
     * must be synchronized externally
     */
    public boolean isLoadingRobotsTxt()
    {
        return this.isLoadingRobotsTxt;
    }

    public void setLoadingRobotsTxt(boolean isLoading)
    {
        this.isLoadingRobotsTxt = isLoading;
        if(isLoading)
        {
            this.queuedRequests = new CachingQueue("HostInfo_" + id + "_QueuedRequests", 100);
        }

    }

    public void setRobotsChecked(boolean isChecked, String[] disallows)
    {
        this.robotTxtChecked = isChecked;
        if(disallows != null)
        {
            this.disallows = disallows;
        }
        else
        {
            this.disallows = emptyKeepOutDirectories;
        }

    }

    public synchronized boolean isAllowed(String path)
    {
        // assume keepOutDirectories is pretty short
        // assert disallows != null
        int length = disallows.length;
        for(int i=0; i<length; i++)
        {
            if(path.startsWith(disallows[i]))
            {
                return false;
            }
        }
        return true;
    }
}
