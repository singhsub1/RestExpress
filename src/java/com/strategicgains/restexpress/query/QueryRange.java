/*
 * Copyright 2011, Strategic Gains, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.strategicgains.restexpress.query;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.exception.BadRequestException;

/**
 * Supports the concept of 'pagination' via request 'Range' header or 'limit'
 * and 'offset' parameters.
 * <p/>
 * Paging is accomplished using the Range and Content-Range HTTP headers or
 * 'limit' and 'offset' query-string parameters.
 * <p/>
 * The client can request a range of results by including the "Range" header
 * with the request. For example, to get the first 25 results:
 * <p/>
 * GET /many_things.json<br/>
 * HTTP/1.1<br/>
 * Host: example.com<br/>
 * Range: items=0-24<br/>
 * <p/>
 * To request the same using the 'limit' and 'offset' parameters, limit would be
 * set to 25 with offset being set to 0 (or empty). For example, via the
 * query-string: &limit=25 which is equivalent to &limit=25&offset=0.
 * <p/>
 * When both 'Range' and 'limit' + 'offset are provided, the 'limit' and
 * 'offset' parameters override the 'Range' header. In other words, the
 * query-string parameters override the headers.
 * <p/>
 * The server will respond with a "Content-Range" header that includes the start
 * and end of the range, as well as a total count of all results. For example,
 * the response for the first 25 of 67 total results:
 * <p/>
 * HTTP/1.1 200 OK<br/>
 * Content-Type: application/json<br/>
 * Content-Range: items 0-24/67<br/>
 * 
 * @author toddf
 * @since Apr 11, 2011
 */
public class QueryRange
{
	// SECTION: CONSTANTS

	private static final String LIMIT_HEADER_NAME = "limit";
	private static final String OFFSET_HEADER_NAME = "offset";
	private static final String RANGE_HEADER_NAME = "Range";
	private static final String ITEMS_HEADER_REGEX = "items=(\\d+)-(\\d+)";
	private static final Pattern ITEMS_HEADER_PATTERN = Pattern.compile(ITEMS_HEADER_REGEX);

	// SECTION: INSTANCE VARIABLES

	private Long offset = null;
	private Integer limit = null;

	// SECTION: CONSTRUCTORS

	public QueryRange()
	{
		super();
	}

	public QueryRange(long offset, int limit)
	{
		super();
		setOffset(offset);
		setLimit(limit);
	}

	// SECTION: ACCESSORS / MUTATORS

	/**
	 * Returns an 'end' value calculated from the offset and limit values
	 * set on this QueryRange. If there is no limit, end is calculated to
	 * be the 'offset' value.
	 * 
	 * @return the computed end value of the range, or offset if there is no limit set.
	 */
	public long getEnd()
	{
		return (hasLimit() ? (getOffset() + getLimit() - 1) : getOffset());
	}

	/**
	 * Returns the limit value or zero if no limit is set.
	 * 
	 * @return the limit of this QueryRange, or zero.
	 */
	public int getLimit()
	{
		return (hasLimit() ? limit.intValue() : 0);
	}

	/**
	 * Answers whether a limit is set on this QueryRange.
	 * 
	 * @return true if a limit is set
	 */
	public boolean hasLimit()
	{
		return (limit != null);
	}

	/**
	 * Set the query limit, which represents the maximum number of results returned
	 * in a query.
	 * 
	 * @param value an integer >= zero
	 * @throws IllegalArgumentException if the limit is less-than zero
	 */
	public void setLimit(int value)
	{
		if (value < 0) throw new IllegalArgumentException("limit must be >= 0");

		this.limit = Integer.valueOf(value);
	}

	/**
	 * Sets the limit of this range by calculating the difference between
	 * the already-set offset and the given 'end' value.
	 *
	 * @param value
	 * @throws IllegalArgumentException if no offset is set or if end is less-than offset, which would cause a negative limit.
	 */
	public void setLimitViaEnd(long value)
	{
		if (!hasOffset()) throw new IllegalArgumentException("Setting 'end' requires 'offset' to be set first");

		setLimit((int) (value - getOffset() + 1));
	}

	/**
	 * getStart() is a synonym for getOffset().
	 * 
	 * @return
	 */
	public long getStart()
	{
		return getOffset();
	}
	
	/**
	 * setStart() is a synonym for setOffset().
	 * 
	 * @param value
	 */
	public void setStart(long value)
	{
		setOffset(value);
	}

	public long getOffset()
	{
		return (hasOffset() ? offset.intValue() : 0);
	}

	public boolean hasOffset()
	{
		return (offset != null);
	}

	public void setOffset(long value)
	{
		if (value < 0) throw new IllegalArgumentException("offset must be >= 0");

		this.offset = Long.valueOf(value);
	}

	/**
	 * Returns true if setLimit(int) and setOffset(long) were both called successfully,
	 * or the constructor QueryRange(long, int) was successfully called.
	 * 
	 * @return true if both a limit and offset are set.
	 */
	public boolean isInitialized()
	{
		return hasLimit() && hasOffset();
	}

	/**
	 * Validates the range.
	 * 
	 * @return true if the range is valid
	 */
	public boolean isValid()
	{
		return (isInitialized()
			&& (getOffset() >= 0)
			&& (getLimit() >= 0));
	}

	// SECTION: FACTORY

	/**
	 * Create a QueryRange instance from the current RestExpress request,
	 * providing a default maximum offset if the request contains no range
	 * criteria.
	 * 
	 * @param request the current request
	 * @param limit the default limit, used if the request contains no range criteria
	 * @return a QueryRange instance, defaulting to 0 to (limit - 1). Never null.
	 */
	public static QueryRange parseFrom(Request request, int limit)
	{
		QueryRange range = new QueryRange();
		range.setOffset(0l);
		range.setLimit(limit);
		parseInto(request, range);
		return range;
	}

	/**
	 * Create a QueryRange instance from the current RestExpress request.
	 * 
	 * @param request the current request
	 * @return a QueryRange instance. Never null.
	 */
	public static QueryRange parseFrom(Request request)
	{
		QueryRange range = new QueryRange();
		parseInto(request, range);
		return range;
	}

	private static void parseInto(Request request, QueryRange range)
	{
		String limit = request.getUrlDecodedHeader(LIMIT_HEADER_NAME);
		String offset = request.getUrlDecodedHeader(OFFSET_HEADER_NAME);

		if (!parseLimitAndOffset(limit, offset, range))
		{
			parseRangeHeader(request, range);
		}
	}

	/**
	 * @param limit
	 * @param offset
	 * @param range
	 * @return
	 */
	private static boolean parseLimitAndOffset(String limit, String offset,
	    QueryRange range)
	{
		boolean hasLimit = false;
		boolean hasOffset = false;

		if (limit != null && !limit.trim().isEmpty())
		{
			hasLimit = true;
			range.setLimit(Integer.parseInt(limit));
		}

		if (offset != null && !offset.trim().isEmpty())
		{
			hasOffset = true;
			range.setOffset(Long.parseLong(offset));
		}
		else if (hasLimit)
		{
			range.setOffset(0l);
		}

		if (hasLimit || hasOffset)
		{
			if (!range.isValid())
			{
				throw new BadRequestException("Invalid 'limit' and 'offset' parameters: limit=" + limit + " offset=" + offset);
			}

			return true;
		}

		return false;
	}

	private static void parseRangeHeader(Request request, QueryRange range)
	{
		String rangeHeader = request.getUrlDecodedHeader(RANGE_HEADER_NAME);

		if (rangeHeader != null && !rangeHeader.trim().isEmpty())
		{
			Matcher matcher = ITEMS_HEADER_PATTERN.matcher(rangeHeader);

			if (!matcher.matches())
			{
				throw new BadRequestException("Unparseable 'Range' header.  Expecting items=[start]-[end] was: " + rangeHeader);
			}
			
			try
			{
				range.setOffset(Long.parseLong(matcher.group(1)));
				range.setLimitViaEnd(Long.parseLong(matcher.group(2)));
			}
			catch(IllegalArgumentException e)
			{
				// swallow it if setOffset() or setLimitViaEnd() causes exception since we're going to call isValid().
			}

			if (!range.isValid())
			{
				throw new BadRequestException("Invalid 'Range' header.  Expecting 'items=[start]-[end]'  was: " + rangeHeader);
			}
		}
	}

	@Override
	public String toString()
	{
		return assembleString().toString();
	}

	/**
	 * Creates a string in the form "items 0-24/66" using the values from this
	 * QueryRange along with the maximum number of items available. This value
	 * is suitable for setting the Content-Range header on the response from
	 * Range requests.
	 * <p/>
	 * No range checking is performed. It is therefore, the caller's
	 * responsibility to ensure that maxItems is greater-than the end value.
	 * 
	 * @param maxItems the maximum number of items available.
	 * @return a String of the form "items <first>-<last>/<max>"
	 */
	public String asContentRange(long maxItems)
	{
		return assembleString(maxItems).append("/").append(maxItems).toString();
	}

	private StringBuffer assembleString()
	{
		return assembleString(null);
	}

	private StringBuffer assembleString(Long max)
	{
		long end = (max == null ? getEnd() : (getEnd() > max ? (max > 0 ? max - 1 : max) : getEnd()));
		return new StringBuffer("items ")
			.append(getOffset())
			.append("-")
		    .append(end);
	}
}
