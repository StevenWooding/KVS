function KVS (data)
{
    this.STATE = { S_KEY: 0, S_VALUE: 1 }
    this.c_state = this.STATE.S_KEY;
	this.kvs_o = {};

    if (data)
    {
        if (typeof data === 'string')
        {
            this.kvs_s = data;
            this.fromString();
        }
        else if (typeof data === 'object') 
        {
            this.kvs_o = data.kvs_o;
            this.toString();
        }
    }
}

KVS.prototype.buffer = "";
KVS.prototype.c_index = 0;

KVS.prototype.ignore = false;
KVS.prototype.m_buffer = "";
KVS.prototype.k_buffer = "";

KVS.prototype.kvs_s = "";
KVS.prototype.key_end = '=';
KVS.prototype.value_end = ';';
KVS.prototype.struct_start = '[';
KVS.prototype.struct_end = ']';
KVS.prototype.meta_start = "~";
KVS.prototype.special = ['=', ';', '[', ']'];

KVS.prototype.removeWhiteSpace = function (input) { return input.replace(/\s/g, ""); }
KVS.prototype.isWhiteSpace = function (input) { return this.removeWhiteSpace(input).length == 0; }
KVS.prototype.removeNewlineTab = function (input) { return input.replace(/[\t\n\r]/gm, ''); }
KVS.prototype.isSpecial = function (input) { for (var char in this.special) if (input === this.special[char]) { return true; } return false; }
KVS.prototype.current = function (i) { return this.kvs_s.charAt(i); }
KVS.prototype.next = function (i) { return (i + 1 <= this.kvs_s.length) ? this.kvs_s.charAt(i + 1) : "" }
KVS.prototype.buffer_first = function () { return (this.buffer && this.buffer.length == 1); }

KVS.prototype.repeatVal = function (val, count)
{
    var ouput = "";
    for (var index = 0; index < count; index++) { ouput += val; }
    return ouput;
}

KVS.prototype.buildStruct = function (index)
{
    var data = {};
    var build_counts = { s_count: 0, k_count: 0 }
    for (this.c_index = index; this.c_index < this.kvs_s.length; this.c_index++) 
    {
        var c = this.current(this.c_index);
        if (c === this.struct_end && this.c_state == this.STATE.S_KEY)
        {
            build_counts.s_count++
            this.buffer = "";
            return data;
        }

        switch (this.c_state)
        {
            case this.STATE.S_KEY: this.readKey(this.c_index, data, build_counts); break;
            case this.STATE.S_VALUE: this.readValue(this.c_index, data); break;
        }
    }
    return data;
}

KVS.prototype.readKey = function (i, data, build_counts)
{
    var c = this.current(i);

    this.buffer += c;
    if (this.buffer_first() == true && this.isWhiteSpace(this.buffer) === true) this.buffer = "";

    if (c == this.key_end || c == this.struct_start)
    {
        this.k_buffer = "";
        var m_I = this.buffer.indexOf(this.meta_start);
        if (m_I > -1)
        {
            this.k_buffer = this.buffer.substring(0, m_I);
        }
        else this.k_buffer = this.buffer;

        this.k_buffer = this.k_buffer.replace(this.struct_start, "");
        this.k_buffer = this.k_buffer.replace(this.struct_end, "");
        this.k_buffer = this.k_buffer.replace(this.key_end, "");

        this.buffer = "";

        if (this.k_buffer) this.k_buffer = this.removeWhiteSpace(this.k_buffer);
        if (this.k_buffer === "" && c === this.key_end) this.k_buffer = build_counts.k_count++;
        if (this.k_buffer === "" && c === this.struct_start) this.k_buffer = build_counts.s_count++;

        if (c == this.struct_start)
        {
            this.c_state = this.STATE.S_KEY;
            data[this.k_buffer] = this.buildStruct(i + 1);
            this.k_buffer = "";
        }
        else this.c_state = this.STATE.S_VALUE
    }
}

KVS.prototype.readValue = function (i, data)
{
    var n = this.next(i);
    var c = this.current(i);

    this.buffer += c;

    if (this.buffer && this.buffer.length == 1 && (this.isWhiteSpace(c) == true || c == this.key_end)) this.buffer = "";

    if (c == this.value_end && n == this.value_end) 
	{	
		this.c_index ++;
		this.ignore = false;
		return;
	}

    if (this.isSpecial(c) == false && this.isSpecial(n) == false) this.ignore = false;

    if (c == this.value_end && this.ignore == false)
    {
        //while (this.buffer.includes(";;")) this.buffer = this.buffer.replace(";;", ";");
        this.buffer = this.buffer.substring(0, this.buffer.length - 1)

        if (this.k_buffer !== "")
        {
            data[this.k_buffer] = this.buffer;
            this.buffer = "";
        }

        this.c_state = this.STATE.S_KEY;
    }
}

KVS.prototype.buildKvs = function (kvs_object, pretty, parent_indent = 0)
{
    var ouput = "";
    var tab = this.repeatVal("\t", parent_indent);

    for (var key in kvs_object)
    {
        var value = kvs_object[key];

        key = (!isNaN(key)) ? "=" : key + "=";

        if (value)
        {
            if (typeof value === 'object')
            {
                key = key.replace(this.value_end, "");
                key = key.replace(this.key_end, "");

                key = (pretty == true && key !== "") ? "\n" + tab + key : key;
                ouput += (pretty == true) ? key + "\n" + tab + "[" + this.buildKvs(value, pretty, parent_indent + 1) + "\n" + tab + "]" : key + "[" + this.buildKvs(value, pretty) + "]"
            }
            else
            {
                ouput += (pretty == true) ? "\n" + tab + key : key;

                var value_escaped = "";

                for (var i = 0; i < value.length; i++) value_escaped += (value[i] == this.value_end) ? ";;" : value[i];

                ouput += value_escaped + ";";
            }
        }
    }

    return ouput;

}

/** ============================== main functions ============================== */

KVS.prototype.add = function (val)
{
    if (!this.kvs_o) this.kvs_o = {};
    var key = this.values().length + "";

    this.kvs_o[key] = val;
    this.kvs_s = this.toString();
    return this.kvs_o;
}

KVS.prototype.clear = function ()
{
    this.kvs_o = {};
    this.kvs_s = "";
}

KVS.prototype.exists = function ()
{
    if (arguments)
    {
        var obj = this.kvs_o;
        var args = Array.prototype.slice.call(arguments);
        if (args)
        {
            for (var i = 0; i < args.length; i++)
            {
                if (!obj || !obj.hasOwnProperty(args[i])) return false;
                obj = obj[args[i]];
            }
        }
        return true;
    }
    else return false;

}

KVS.prototype.fromString = function (kvs_s)
{
    if (kvs_s) this.kvs_s = kvs_s;
    return this.kvs_o = this.kvs_s ? this.buildStruct(0) : {};
}

KVS.prototype.get = function ()
{
    if (arguments)
    {
        var obj = this.kvs_o;
        var args = Array.prototype.slice.call(arguments);
        for (var i = 0; i < args.length; i++)
        {
            if (!obj || !obj.hasOwnProperty(args[i])) return "";
            obj = obj[args[i]];
        }
        return typeof obj === 'object' ? "" : obj;
    }
    else "";
}

KVS.prototype.getFirstKey = function ()
{
    var keys = this.keys();
    return keys && keys.length > 0 ? keys[0] : "";
}

KVS.prototype.getFirstValue = function ()
{
    var vals = this.values();
    return vals && vals.length > 0 ? vals[0] : "";
}

KVS.prototype.getKvs = function (...args)
{
    if (args && args.length>0)
    {
		var result = new KVS();
		result.kvs_o = this.getOrCreateNativeObj(...args);
		return result;
    }
	else
		return this;

}

KVS.prototype.getNative = function (...args)
{
	if(args && args.length >0)
	{
		var o = this.getOrCreateNativeObj(...args.slice(0,args.length-1));

		var lastKey = args[args.length-1];
		if(!o[lastKey]) o[lastKey] = {};

		return o[lastKey];
	}
	else
		return this.kvs_o;
}

KVS.prototype.keys = function ()
{
    return this.kvs_o ? Object.keys(this.kvs_o) : [];
}

KVS.prototype.merge = function (kvs)
{
    for (var attrname in kvs.kvs_o) { this.kvs_o[attrname] = kvs.kvs_o[attrname]; }

    this.toString();
    return this.kvs_o;
}

KVS.prototype.remove = function ()
{
    if (arguments)
    {
        var obj = this.kvs_o;
        var args = Array.prototype.slice.call(arguments);
        var key_main = "";
        if (args)
        {
            key_main = args && args.length > 0 ? args[args.length - 1] : "";

            for (var i = 0; i < args.length; i++)
            {
                var key = args[i];
                if (obj && obj.hasOwnProperty(key))
                {
                    if (key == key_main) break;
                    obj = obj[key];
                }
            }
        }

        if (obj[key_main]) delete obj[key_main];
    }
    this.toString();
}

KVS.prototype.toString = function (pretty = false, wrap = null)
{
    if (!this.kvs_o) return "";
    else 
    {
        this.kvs_o = (wrap) ? { [wrap]: this.kvs_o } : this.kvs_o;

        this.kvs_s = this.buildKvs(this.kvs_o, pretty);

        return this.kvs_s;
    }
}

KVS.prototype.values = function ()
{
    return this.kvs_o ? Object.values(this.kvs_o) : [];
}

KVS.prototype.getOrCreateNativeObj = function(...args)
{
	var o = this.kvs_o;

	if(args && args.length>0)
	{
		for (var i = 0; i < args.length; i++)
		{
			var key = args[i];
			if(!o[key] || typeof(o[key])!="object") o[key] = {};
			o = o[key];
		}
	}
	
	return o;
}

KVS.prototype.set = function (value, ...args)
{
	var o = this.getOrCreateNativeObj(...args.slice(0,args.length-1));
	o[args[args.length-1]] = value;
}











