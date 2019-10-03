package slite.lib.java;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Collection;
import java.util.Map;

public class KVS
{
	private LinkedHashMap<String, Object> map = new LinkedHashMap<>();
	private int autoKey = 0;

	/**
	 * Construct a new KVS structure and fill it with the decoded data contained in text
	 * @param text
	 */
	public KVS(String text)
	{
		this.fromString(text);
	}
	
	/**
	 * Construct a new KVS structure and fill it with the decoded data inside the
	 * stream, but only read 'itemCount' items from the stream. If the stream
	 * ends sooner than itemCount items are received then the KVS is returned with
	 * only the items read from the stream.
	 * 
	 * If itemCount is set to -1 then it will read till the end of the stream.
	 * 
	 * This operation might block depending on the blocking mode set on the stream. 
	 * 
	 * This operation does not support non-blocking streams.
	 * 
	 * @param stream The stream to read from
	 * @param itemCount The number of items (key-value pairs) to read. if -1 it will
	 * read till the end of the stream.
	 */
	public KVS(InputStream stream, int itemCount) throws Exception
	{
		this.fromStream(stream, itemCount);
	}

	/**
	 * Construct an empty KVS structure
	 */
	public KVS()
	{

	}

	private KVS(KVSBufferIn buffer)
	{
		this.fromBuffer(buffer, -1);
	}

	/**
	 * Decode an existing KVS string into this KVS structure.
	 * Any existing data inside this KVS structure will be overwritten
	 * if the keys match the incoming structure.
	 * @param text
	 */
	public void fromString(String text)
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(text.getBytes());
		
		KVSBufferIn buffer = new KVSBufferIn(bais);
		this.fromBuffer(buffer, -1);
		
		try { bais.close(); } catch (IOException ex) {}
	}
	
	/**
	 * Load KVS from file. This will merge over the existing KVS data.
	 * @param file
	 * @throws Exception 
	 */
	public void fromFile(String file) throws Exception
	{
		this.fromFile(new File(file));
	}
	
	/**
	 * Load KVS from file. This will merge over the existing KVS data.
	 * @param file
	 * @throws Exception 
	 */
	public void fromFile(File file) throws Exception
	{
		FileInputStream fis = new FileInputStream(file);
		this.fromStream(fis, -1);
		fis.close();
	}
	
	/**
	 * Reads and populates this KVS from a string. But only reads the number of 
	 * itemCount key-value pairs before it stops reading. This method will block
	 * until the itemCount is saturated or the stream throws and error or get's disconnected.<br>
	 * <br>
	 * All existing data in this KVS with the same keys will be overwritten by the incoming data.
	 * <br>
	 * <br>
	 * @param stream The stream to read the KVS from
	 * @param itemCount The number of key-value pairs to attempt to read.
	 * This is commonly set to 1 if the KVS written to the stream was wrapped.
	 * @return The number of key-value pairs read from the stream
	 * @throws Exception Throws and EOFException if there was an error on the stream
	 * and no key-value pairs could be read. If at least one key-value pair was read then an 
	 * exception will NOT be thrown, and the method will return as usual.
	 */
	public int fromStream(InputStream stream,int itemCount) throws Exception
	{
		KVSBufferIn buffer = new KVSBufferIn(stream);
		int count = this.fromBuffer(buffer, itemCount);
		if(count==0) throw new EOFException();
		return count;
	}

	private int fromBuffer(KVSBufferIn buffer, int itemCount)
	{
		int autoId = 0;
		int count = 0;
		try
		{
			while(itemCount==-1 || count < itemCount)
			{
				String key = this.fromBufferKey(buffer);
				if(key==null) break;
				if(key.isEmpty())
				{
					key = autoId+"";
					autoId++;
				}

				Object value = this.fromBufferValue(buffer);
				this.map.put(key, value);
				
				//System.out.println(key + " "+ value);
				
				count++;
			}
		}
		catch(IOException e)
		{
			// at end of stream
		}

		return count;
	}

	/**
	 * Reads the next key from the buffer.
	 * If the key returned is null it means that the end of the file or end of the structure has been reached.
	 * @param buffer
	 * @return The key read. If null it means that the structure or file has ended and there aren't anymore key-value pairs in this structure or string
	 */
	private String fromBufferKey(KVSBufferIn buffer) throws IOException
	{
		StringBuilder key = null;
		char next;

		while(true)
		{
			next = buffer.next();
			if(next==']') // this is the end of the current structure
			{
				break;
			}
			else if(next=='[' || next=='=') // We are finished reading a key
			{
				if(key==null) return "";
				else return key.toString().trim();
			}
			else if(next=='~') // We are finished reading a key, but we first have to read through metadata to get the stream to the correct position.
			{
				while(next!='[' && next!='=') next = buffer.next(); // Reading through meta data (future protocol)
				if(key==null) return "";
				else return key.toString().trim();
			}
			else if(key!=null || (next!='\n' && next!='\r' && next!='\t' && next!=' '))
			{
				if(key==null) key = new StringBuilder();
				key.append(next);
			}
		}

		return null; // We are at the end of the string and haven't gotten a key, so return null to signal end of string or end of structure
	}

	private Object fromBufferValue(KVSBufferIn buffer) throws IOException
	{
		char prev = buffer.prevChar();
		if(prev=='=')
			return this.fromBufferValuePrimitive(buffer);
		else // the only other option is that it's a structuve
			return new KVS(buffer);
	}

	private String fromBufferValuePrimitive(KVSBufferIn buffer) throws IOException
	{
		StringBuilder value = null;
		char next;
		while(true)
		{
			next = buffer.next();
			if(next==';')
			{
				try
				{
					if(buffer.peek()!=';') break;
					else
						buffer.next(); // just read past the next character
				}
				catch(IOException e)
				{
					break;
				}
			} 

			if(value==null) value = new StringBuilder();
			value.append(next);
		}

		if(value==null) return "";
		else return value.toString();
	}

	/**
	 * returns true if this KVS is empty and false if it's not.
	 * @return 
	 */
	public boolean isEmpty()
	{
		return this.map.isEmpty();
	}

	/**
	 * Streams this KVS to the provided output stream, no wrapping will occur and 
	 * the data will not be formatted in any way, thus it will be as lightweight as
	 * possible without using any compression.
	 * @param out
	 * @throws Exception 
	 */
	public void toStream(OutputStream out) throws Exception
	{
		this.toStream(out, false, null);
	}
	
	/**
	 * Streams this KVS to the provided output stream, with the options of wapping it
	 * with the specified wrapKey, and also additionally formatting it in a pretty
	 * way so that it's easily human readable.
	 * @param out The output stream to stream this KVS to
	 * @param pretty If set to true it will format it in a very human friendly way,
	 * but at the cost of additional space used.
	 * @param wrapKey If set to NULL then this KVs will NOT be wrapped. If set to 
	 * a blank string then this KVS will be wrapped without a key. If the wrapkey 
	 * is set to a non-blank string then the KVS will be wrapped with the key specified
	 * @throws Exception 
	 */
	public void toStream(OutputStream out, boolean pretty, String wrapKey) throws Exception
	{
		out.write(this.toString(pretty, wrapKey).getBytes());
		out.flush();
	}
	
	/**
	 * Encode this KVS into a string that is as small as possible while preserving all data and structure.
	 */
	public String toString()
	{
		return this.toString(false,null);
	}

	/**
	 * Encode this KVS into a string, but make it pretty so that human can read it with ease.
	 * @param pretty Set to true if you want it pretty. If set to false it's equal to calling .toString();
	 * @param wrapKey if null the KVS would be converted to a string as is. If set
	 * to non-null it would be wrapped with [] so that the entire KVS acts as a sub
	 * structure. The value of the string would be written before the opening square
	 * bracket.
	 * @return The encoded string
	 */
	public String toString(boolean pretty, String wrapKey)
	{
		StringBuilder buffer = new StringBuilder();
		this.toString(buffer, wrapKey, pretty ? "" : null);

		return buffer.toString();
	}

	private void toString(StringBuilder buffer, String wrapKey, String indent)
	{
		if(wrapKey!=null)
		{
			if(indent==null) buffer.append(wrapKey+"[");
			else buffer.append("\n"+indent+wrapKey+"[\n");
		}

		if(!map.isEmpty())
		{
			int autoIdInt = 0;
			String autoIdString = autoIdInt+"";
			Set<Entry<String,Object>> entries = map.entrySet();
			String key;
			String indentSub = (indent == null ? null : (wrapKey!=null ? indent + "\t" : indent));
			for(Entry<String,Object> entry : entries)
			{
				key = entry.getKey();
				if(key.equals(autoIdString))
				{
					key = "";
					autoIdInt++;
					autoIdString = autoIdInt + "";
				}
				this.toStringKey(buffer,key,indentSub);
				Object value = entry.getValue();
				if(value!=null && value.getClass().equals(KVS.class))
					((KVS)value).toString(buffer, "", indentSub);
				else
					this.toStringValue(buffer,value+"", indentSub);
			}
		}

		if(wrapKey!=null)
		{
			if(indent==null) buffer.append("]");
			else buffer.append(indent+"]\n");
		} 
	}

	private void toStringKey(StringBuilder buffer, String key, String indent) // This is a single line function for now so that we can add metadata later on
	{
		buffer.append(indent==null ? key : indent + key);
	}

	private void toStringValue(StringBuilder buffer, String value, String indent)
	{
		buffer.append("="+value.replace(";", ";;")+";" + (indent == null ? "" : "\n"));
	}
	
	private final static String KEY_COLOUR = "\033[0;36m";
	private final static String KEY_COLOUR2 = "\033[0;97m";
	private final static String VALUE_COLOUR = "\033[0;35m";
	private final static String NO_COLOUR = "\033[0m";
	
	/**
	 * "Colourises" and pretty prints this KVS for improved readability in the terminal / console.
	 * @param wrap
	 * @return 
	 */
	public String toStringColourful(String wrap) { return toStringColourful(this, 0, wrap); }
	private String tabs(int t) {
		String result = "" ;
		for (int i=0; i< t; i++) result += "\t";
		return result;
	}
	private String colourise(String text, String colour) { return colour + text + NO_COLOUR; }
	private String toStringColourful(KVS kvs, int depth, String wrap) {
		if (wrap != null) depth+=1;
		String indent = tabs(depth);
		String result = (wrap == null ? "" : colourise(wrap, KEY_COLOUR) + "[\n") ;
		int count = 0;
		for (Map.Entry<String, Object> entry : kvs.entries()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (key.equals(count + "")) key = "" ;
			if (value instanceof String) result += indent + colourise(key, KEY_COLOUR2) + "=" + colourise(value.toString(), VALUE_COLOUR) + ";";
			else result += indent + colourise(key, KEY_COLOUR) + "\n" + 
							indent + "[\n" + 
									toStringColourful((KVS) value, depth+1, null) + "\n" + 
							indent + "]";
			if (count++ < kvs.size()-1) result += "\n";
		}
		return (wrap==null ? result : result + "\n]");
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive long. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */
	public long getLong(String... keys)
	{
		return this.getLong(0, keys);
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive long. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */
	public long getLong(long defaultValue, String... keys)
	{
		long result = 0;
		try{ result = Long.parseLong(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}
	
	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive int. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */	
	public int getInt(String... keys)
	{
		return this.getInt(0, keys);
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive int. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */	
	public int getInt(int defaultValue, String... keys)
	{
		int result = 0;
		try{ result = Integer.parseInt(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}
	
	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive short. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */	
	public short getShort(String... keys)
	{
		return this.getShort((short)0, keys);
	}
	
	/**
	 * This method checks if the specified value exists or not. If it does exist it
	 * would be returned. If not then the defaultValue is returned
	 * @param defaultValue
	 * @param keys
	 * @return 
	 */
	public String getString(String defaultValue, String... keys)
	{
		if(this.exists(keys)) return this.get(keys);
		return defaultValue;
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive short. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */	
	public short getShort(short defaultValue, String... keys)
	{
		short result = 0;
		try{ result = Short.parseShort(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}
	
	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive byte. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */
	public byte getByte(String... keys)
	{
		return this.getByte((byte)0, keys);
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive byte. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */	
	public byte getByte(byte defaultValue, String... keys)
	{
		byte result = 0;
		try{ result = Byte.parseByte(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive double. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */	
	public double getDouble(String... keys)
	{
		return this.getDouble(0, keys);
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive double. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */	
	public double getDouble(double defaultValue, String... keys)
	{
		double result = 0;
		try{ result = Double.parseDouble(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive float. If the result cannot be converted
	 * then a 0 is returned.
	 * @param keys
	 * @return 
	 */	
	public float getFloat(String... keys)
	{
		return this.getFloat(0, keys);
	}

	/**
	 * This method internally calls the get(String... keyPath) method, but additionally
	 * coverts the result returned to a primitive float. If the result cannot be converted
	 * then the specified defaultValue is returned.
	 * @param keys
	 * @return 
	 */	
	public float getFloat(float defaultValue, String... keys)
	{
		float result = 0;
		try{ result = Float.parseFloat(this.get(keys)); }catch(Exception e){ result = defaultValue; };
		return result;
	}
	
	/**
	 * Get a specific primitive value string referenced by the key path. 
	 * If the key-value pair does not exist then an empty string is returned.
	 * If the key-value pair is another KVM structure then the encoded string of 
	 * that KVM structure is returned.
	 * @param keys The key path
	 * @return A string version of the value that the key path is referencing.
	 */
	public String get(String... keys)
	{
		KVS kvs = this;
		String result = "";
		Object obj;
		int keyCount = keys.length-1;
		int counter = 0;
		for(String key : keys)
		{
			obj = kvs.map.get(key);
			if(obj==null) return "";

			if(counter<keyCount) // use this up until the second last key
			{
				if(!obj.getClass().equals(String.class)) // only continue if all keys except the second last key is a kvs
					kvs = (KVS)obj;
				else
					return "";
			}
			else // only on the last key extract the result as a String
				result = obj.toString().trim();

			counter++;
		}

		return result;
	}
	
	/**
	 * This method assists in fetching dynamic data. If the type of the keyPath is
	 * a primitive value then the method would return a string array with only one 
	 * element. If the type of the keyPath is a KVS structure, then the values of that
	 * kvs structure would be returned as a string array.
	 * <p>
	 * Thus you can setup 2 different KVS structures as such:
	 * <pre>
	 * structure 1
	 * [
	 *		names = Peter
	 * ]
	 * 
	 * structure 2
	 * [
	 *		names
	 *		[
	 *			=Peter;
	 *			=John;
	 *			=Kyle;
	 *		]
	 * ]
	 * </pre>
	 * 
	 * But you can fetch the value of the items using kvs.getList("names"); regardless
	 * of the 2 different structures. The first structure would simply return a
	 * string array with only one element with the value Peter. The second structure
	 * would return an array with 3 elements namely Peter, John and Kyle.
	 * </p>
	 * <p>
	 * If the keyPath does not exist then an empty array is returned
	 * </p>
	 * @param keyPath The path inside the KVS to return
	 * @return a String array of the data.
	 */
	public String[] getList(String... keyPath)
	{
		if(keyPath==null || keyPath.length==0)
		{
			String[] result = this.map.values().toArray(new String[0]);
			this.trimArray(result);
			return result;
		}

		Object value = this.getNative(keyPath);
		if(value==null) return new String[0];
		if(value.getClass().equals(KVS.class))
		{
			KVS kvs = (KVS) value;
			String[] result = kvs.values().toArray(new String[0]);
			this.trimArray(result);
			return result;
		}
		else
		{
			return new String[]{value.toString().trim()};
		}
	}
	
	/**
	 * This method gets a sub structure of the specified keyPath and returns a map of it.
	 * This method will never return null. And the possible options are:<br>
	 * <ul>
	 *	<li>A map that contains all the keys and values as strings pointed to by
	 * the sub-structure, if it exists and is a sub-structure. If the sub structure
	 * contains additional sub-structures they will be converted to a serialized KVS string</li>
	 *	<li>An empty map if the key does not exist, or if it does exist but is empty.</li>
	 *	<li>A map with only one entry, with the key being an empty string "" and
	 * the value being the value of the specified keyPath if the keyPath referenced
	 * is NOT a structure but a primitive value</li>
	 * </ul>
	 * @param keyPath
	 * @return 
	 */
	public Map<String, String> getMap(String... keyPath)
	{
		Map<String, String> result = new LinkedHashMap<>();
		
		Object value = this.getNative(keyPath);
		if(value==null) return result;
		if(value.getClass().equals(KVS.class))
		{
			KVS kvs = (KVS) value;
			Set<Entry<String, Object>> entries = kvs.entries();
			for(Entry<String, Object> entry : entries)
			{
				value = entry.getValue();
				if(value==null) value = "";
				result.put(entry.getKey(), entry.getValue().toString());
			}
		}
		else
			result.put("", (String) value);
		
		return result;
	}
	
	/**
	 * This method will bypass any null pointer checking and will return what is
	 * at the location specified by the keyPath. It might return 3 things:
	 * <p>
	 * 1. A String if the value pointed to by the keyPath is a String<br>
	 * 2. A KVS structure if the value pointed to by the keyPath is a KVS structure<br>
	 * 3. A NULL if there is no value pointed to by the keyPath<br>
	 * </p>
	 * @param keyPath
	 * @return Object that can be NULL or a String or a KVS
	 */
	public Object getNative(String... keyPath)
	{
		KVS kvs = this;
		Object result = null;
		Object obj;
		int keyCount = keyPath.length-1;
		int counter = 0;
		for(String key : keyPath)
		{
			obj = kvs.map.get(key);
			if(obj==null) return null;

			if(counter<keyCount) // use this up until the second last key
			{
				if(!obj.getClass().equals(String.class)) // only continue if all keys except the second last key is a kvs
					kvs = (KVS)obj;
				else
					return null;
			}
			else // only on the last key extract the result as a String
				result = obj;

			counter++;
		}

		return result;		
	}

	/**
	 * Get a KVS referenced by the key path. If the key path reference
	 * is NOT a KVS structure then one will be created at that key path and returned.
	 * @param keys The key path
	 * @return The KVS structure at the key path reference.
	 */
	public KVS getKvs(String... keys)
	{
		KVS kvs = this;
		Object obj;
		for(String key : keys)
		{
			obj = kvs.map.get(key);
			if(obj!=null && obj.getClass().equals(KVS.class))
				kvs = (KVS)obj;
			else
				kvs.map.put(key, kvs = new KVS());
		}

		return kvs;
	}
	
	

	/**
	 * Set a specific value of a key-value pair referenced by the key path
	 * @param value The value to set, can be any object. The toString() method 
	 * will be called on that object to get a string version of the object when
	 * this KVS is encoded into a string. 
	 * @param keys The key path
	 * @return The current KVS structure. For chaining purposes.
	 */
	public KVS set(Object value, String... keys)
	{
		KVS kvs = this;
		Object obj;
		int keyCount = keys.length-1;
		int counter = 0;		
		for(String key : keys)
		{
			obj = kvs.map.get(key);

			if(counter<keyCount) // use this up until the second last key
			{
				if(obj==null || obj.getClass().equals(String.class))
					kvs.map.put(key, kvs=new KVS());
				else
					kvs = (KVS)obj;
			}
			else // only on the last key to put the value
				kvs.map.put(key, value);

			counter++;
		}
		return this;
	}
	
	/**
	 * This method functions exactly like the set(Object value, String... keys) method
	 * except for the fact that it first does a check if the key already exists.
	 * If the key does already exist then nothing is done. If it does not exist then
	 * it will be set to the value specified.
	 * @param value
	 * @param keys
	 * @return The current KVS structure. For chaining purposes.
	 */
	public KVS setDefault(Object value, String... keys)
	{
		KVS kvs = this;
		Object obj;
		int keyCount = keys.length-1;
		int counter = 0;		
		for(String key : keys)
		{
			obj = kvs.map.get(key);

			if(counter<keyCount) // use this up until the second last key
			{
				if(obj==null || obj.getClass().equals(String.class))
					kvs.map.put(key, kvs=new KVS());
				else
					kvs = (KVS)obj;
			}
			else if(!kvs.map.containsKey(key)) // only on the last key to put the value, if the key does not exist already
				kvs.map.put(key, value);

			counter++;
		}
		return this;		
	}
	
	/**
	 * returns the number of entries on the first structure level of the current
	 * KVS. The key-value pairs inside any sub-structure would NOT be counted.
	 * @return 
	 */
	public int size()
	{
		return this.map.size();
	}
	
	/**
	 * Add a value to this KVS. This uses an auto incremented key and internally calls
	 * the set(Object value, String... keyPath) method using the auto incremented key.
	 * This makes it very easy to use the KVS as an array and not a map.
	 * @param value 
	 */
	public KVS add(Object value)
	{
		this.set(value, this.autoKey+"");
		this.autoKey++;
		return this;
	}
	
	/**
	 * Simply calls the add(Object value) method multiple times while iterating
	 * through the values array.
	 * @param values 
	 */
	public void addAll(String[] values)
	{
		for(String value : values) this.add(value);
	}

	/**
	 * Remove a specific key-value pair referenced by the key path
	 * @param keys The key path
	 */
	public void remove(String... keys)
	{
		KVS kvs = this;
		Object obj;
		int keyCount = keys.length-1;
		int counter = 0;
		for(String key : keys)
		{
			obj = kvs.map.get(key);
			if(obj==null) return;

			if(counter<keyCount) // use this up until the second last key
			{
				if(!obj.getClass().equals(String.class)) // only continue if all keys except the second last key is a kvs
					kvs = (KVS)obj;
				else
					return;
			}
			else // only on the last key extract the result as a String
				kvs.map.remove(key);

			counter++;
		}
	}
	/**
	 * Get all the keys in this KVS's root structure as a Set.
	 * @return The keys
	 */
	public Set<String> keySet()
	{
		return this.map.keySet();
	}

	/**
	 * Gets and returns the first entry in the KVS. The value part of the entry
	 * can be either a String or another KVS structure.
	 * @return 
	 */
	public Entry<String, Object> getFirst()
	{
		Set<Entry<String, Object>> entries = this.map.entrySet();
		for(Entry<String, Object> entry : entries)
			return entry;

		return new AbstractMap.SimpleEntry<>("","");
	}
	
	/**
	 * Returns the first key inside this KVS
	 * @return 
	 */
	public String getFirstKey()
	{
		for(String key: this.map.keySet()) return key;
		
		return "";
	}
	
	/**
	 * Returns the first value inside this KVS. If this KVS is empty then an empty
	 * string is returned. If the first value is a KVS then a KVS is returned. If 
	 * the frist value is a String then a String is returned.
	 * @return 
	 */
	public Object getFirstValue()
	{
		for(Object value : this.map.values()) return value;
		
		return "";
	}
	
	/**
	 * Get the values only of this KVS's root structure. The Objects inside the Collection that
	 * is returned will either be of types String or KVS. No other types are allowed.
	 * @return
	 */
	public Collection<Object> values()
	{
		return this.map.values();
	}

	/**
	 * Get all the entries in this KVS's root structure as an entry set. The values inside
	 * each entry can be either a String or a KVS. No other types are allowed.
	 * @return All the entries in this KVS's root structure
	 */
	public Set<Entry<String, Object>> entries()
	{
		return this.map.entrySet();
	}

	/**
	 * Finds out if a specified keyPath exists inside this KVS. If it exists it
	 * returns true, if not it returns false.
	 * @param keyPath
	 * @return 
	 */
	public boolean exists(String ...keyPath)
	{
		KVS kvs = this;
		Object obj;
		int keyCount = keyPath.length-1;
		int counter = 0;
		for(String key : keyPath)
		{
			obj = kvs.map.get(key);
			if(obj==null) return false;

			if(counter<keyCount) // use this up until the second last key
			{
				if(!obj.getClass().equals(String.class)) // only continue if all keys except the second last key is a kvs
					kvs = (KVS)obj;
				else
					return false;
			}

			counter++;
		}

		return true;		
	}
	
	/**
	 * Return all the keys in this KVS's root structure as a String array.
	 * @return The keys
	 */
	public String[] keys()
	{
		Set<String> keySet = this.map.keySet();
		return keySet.toArray(new String[keySet.size()]);
	}
	
	
	/**
	 * Merges the given KVS into this one. The given KVS would take preference when 
	 * any conflicts occur where this KVS and the given one has the same keys.
	 * @param kvs 
	 */
	public void merge(KVS kvs)
	{
		Set<Entry<String, Object>> entries = kvs.map.entrySet();
		for(Entry<String, Object> entry : entries)
		{
			String key = entry.getKey();
			Object currentValue = this.map.get(key);
			Object value = entry.getValue();
			if(value.getClass().equals(KVS.class) && currentValue!=null && currentValue.getClass().equals(KVS.class))
				((KVS)currentValue).merge((KVS) value);
			else
				this.map.put(key, value);
		}
	}
	
	
	public static void trimArray(String[] items)
	{
		int s = items.length;
		for(int i=0;i<s;i++)
			items[i] = items[i].trim();
	}

	public static void main(String[] args) throws Exception
	{
		FileInputStream fis = new FileInputStream("test.kvs");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		long time,time2;

		
		time = System.currentTimeMillis();
		String text;
		
		
		/*
		
		
		int read = 0;
		byte[] buffer = new byte[1024];
		while((read=fis.read(buffer))>-1) baos.write(buffer, 0, read);

		String text = new String(baos.toByteArray());
		time = System.currentTimeMillis();
		KVS kvs = new KVS(text);
		//*/
		
		//*

		KVS kvs = new KVS(fis, -1);

		kvs.set(32,"age");
		kvs.set("diesel","car","0","fuel");
		kvs.set("petrol","car","1","fuel");

//		System.out.println(kvs);
		time2 = System.currentTimeMillis();
		System.out.println("Decode Pretty: "+(time2-time)+" ms");
		
		//*/
		

		time = System.currentTimeMillis();
		text = kvs.toString(false,null);
		time2 = System.currentTimeMillis();
		System.out.println("Encode Small: "+(time2-time)+" ms");
	

		time = System.currentTimeMillis();
		kvs = new KVS(text);
		time2 = System.currentTimeMillis();
		System.out.println("Decode Small: "+(time2-time)+" ms");


		time = System.currentTimeMillis();
		text = kvs.toString(true,null);
		time2 = System.currentTimeMillis();
		System.out.println("Encode Pretty: "+(time2-time)+" ms");

		System.out.println(text);
		
		//System.out.println(kvs.getFirst());
		//*/
	}
}

class KVSBufferIn
{
	BufferedReader reader;
	char prevChar = 0;

	public KVSBufferIn(InputStream in)
	{
		reader = new BufferedReader(new InputStreamReader(in));
	}

	public char next() throws IOException
	{	
		prevChar = (char) reader.read();
		if(prevChar==-1 || prevChar==0xFFFF) throw new IOException("EOF");
		return prevChar;
	}
	
	public char prevChar()
	{
		return prevChar;
	}

	public char peek() throws IOException
	{
		reader.mark(2);
		char result = (char) reader.read();
		reader.reset();
		return result;
	}
}
