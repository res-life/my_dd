/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.indexing.overlord.setup;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 */
public class GalaxyUserData
{
  public final String env;
  public final String version;
  public final String type;

  @JsonCreator
  public GalaxyUserData(
      @JsonProperty("env") String env,
      @JsonProperty("version") String version,
      @JsonProperty("type") String type
  )
  {
    this.env = env;
    this.version = version;
    this.type = type;
  }

  @JsonProperty
  public String getEnv()
  {
    return env;
  }

  @JsonProperty
  public String getVersion()
  {
    return version;
  }

  @JsonProperty
  public String getType()
  {
    return type;
  }

  public GalaxyUserData withVersion(String ver)
  {
    return new GalaxyUserData(env, ver, type);
  }

  @Override
  public String toString()
  {
    return "GalaxyUserData{" +
           "env='" + env + '\'' +
           ", version='" + version + '\'' +
           ", type='" + type + '\'' +
           '}';
  }
}
