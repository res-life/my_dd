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

package io.druid.firehose.kafka;

import com.google.common.base.Charsets;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.metamx.common.exception.FormattedException;
import com.metamx.common.logger.Logger;

import io.druid.data.input.ByteBufferInputRowParser;
import io.druid.data.input.Firehose;
import io.druid.data.input.FirehoseFactory;
import io.druid.data.input.InputRow;
import io.druid.data.input.MapBasedInputRow;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 */
public class KafkaEightFirehoseFactory implements FirehoseFactory<ByteBufferInputRowParser>
{
  private static final Logger log = new Logger(KafkaEightFirehoseFactory.class);

  @JsonProperty
  private final Properties consumerProps;

  @JsonProperty
  private final String feed;

  @JsonProperty
  private final ByteBufferInputRowParser parser;

  @JsonCreator
  public KafkaEightFirehoseFactory(
      @JsonProperty("consumerProps") Properties consumerProps,
      @JsonProperty("feed") String feed,
      // backwards compatible
      @JsonProperty("parser") ByteBufferInputRowParser parser
  )
  {
    this.consumerProps = consumerProps;
    this.feed = feed;
    this.parser = (parser == null) ? null : parser;
  }

  @Override
  public Firehose connect(final ByteBufferInputRowParser firehoseParser) throws IOException
  {
    Set<String> newDimExclus = Sets.union(
        firehoseParser.getParseSpec().getDimensionsSpec().getDimensionExclusions(),
        Sets.newHashSet("feed")
    );
    final ByteBufferInputRowParser theParser = firehoseParser.withParseSpec(
        firehoseParser.getParseSpec()
                      .withDimensionsSpec(
                          firehoseParser.getParseSpec()
                                        .getDimensionsSpec()
                                        .withDimensionExclusions(
                                            newDimExclus
                                        )
                      )
    );

    final ConsumerConnector connector = Consumer.createJavaConsumerConnector(new ConsumerConfig(consumerProps));

    final Map<String, List<KafkaStream<byte[], byte[]>>> streams = connector.createMessageStreams(
        ImmutableMap.of(
            feed,
            1
        )
    );

    final List<KafkaStream<byte[], byte[]>> streamList = streams.get(feed);
    if (streamList == null || streamList.size() != 1) {
      return null;
    }

    final KafkaStream<byte[], byte[]> stream = streamList.get(0);
    final ConsumerIterator<byte[], byte[]> iter = stream.iterator();

    return new Firehose()
    {
      @Override
      public boolean hasMore()
      {
        return iter.hasNext();
      }

      @Override
      public InputRow nextRow() throws FormattedException
      {
    	MessageAndMetadata<byte[], byte[]> msg = iter.next();
        final byte[] message = msg.message();
        int partition = msg.partition();
        

        if (message == null) {
          return null;
        }

        try {
        	InputRow row = theParser.parse(ByteBuffer.wrap(message));
        	if(row instanceof MapBasedInputRow){
        		MapBasedInputRow mapRow = (MapBasedInputRow) row;
        		mapRow.getDimensions().add("kafka_partition_this_only_for_log");
        		mapRow.getEvent().put("kafka_partition_this_only_for_log", partition);
        	}

          return row;
        }
        catch (Exception e) {
          throw new FormattedException.Builder()
              .withErrorCode(FormattedException.ErrorCode.UNPARSABLE_ROW)
              .withMessage(String.format("Error parsing[%s], got [%s]", new String(message, Charsets.UTF_8), e.toString()))
              .build();
        }
      }

      @Override
      public Runnable commit()
      {
        return new Runnable()
        {
          @Override
          public void run()
          {
               /*
                 This is actually not going to do exactly what we want, cause it will be called asynchronously
                 after the persist is complete.  So, it's going to commit that it's processed more than was actually
                 persisted.  This is unfortunate, but good enough for now.  Should revisit along with an upgrade
                 of our Kafka version.
               */

            log.info("committing offsets");
            connector.commitOffsets();
          }
        };
      }

      @Override
      public void close() throws IOException
      {
        connector.shutdown();
      }
    };
  }

  @Override
  public ByteBufferInputRowParser getParser()
  {
    return parser;
  }
}
