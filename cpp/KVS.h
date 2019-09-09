/*
 * KVS.h
 *
 *  Created on: 13 Feb 2019
 *      Author: hansen
 */

#ifndef KVS_H_
#define KVS_H_

#include <map>
#include <iostream>
#include <typeinfo>
#include <cstring>
#include <algorithm>
#include <cctype>
#include <locale>
#include <vector>
#include <regex>
#include <cstdarg>

#define KVS_VALUE_STRING 1
#define KVS_VALUE_STRUCT 0

using namespace std;

// trim from start (in place)
static inline void ltrim(std::string &s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](int ch) {
        return !std::isspace(ch);
    }));
}

// trim from end (in place)
static inline void rtrim(std::string &s) {
    s.erase(std::find_if(s.rbegin(), s.rend(), [](int ch) {
        return !std::isspace(ch);
    }).base(), s.end());
}

// trim from both ends (in place)
static inline void trim(std::string &s) {
    ltrim(s);
    rtrim(s);
}

static inline string do_replace( string const & in, string const & from, string const & to )
{
  return std::regex_replace( in, std::regex(from), to );
}


class KVS_Value {
public:
	KVS_Value(){}
	KVS_Value(int t, void* data, long size){
		type = t;
		value = malloc(size);
		memcpy(value, data, size);
	}
	virtual ~KVS_Value(){}
	int type;
	void* value;
private:


};

class KVS {
public:
	KVS();
	KVS(string kvs, int index = 0);
	KVS(const KVS&);
	void fromString(string kvs, int &kvs_index);
	string toString();
	void Debug(map<string,KVS_Value> active);
	void Clear();
	void Add(KVS_Value value);
	bool Exists(int count, ...);
	map<string, KVS_Value> dictionary;
	vector<string> keys;
	vector<KVS_Value> values;
	virtual ~KVS();
private:
	string __dict_to_kvs(map<string,KVS_Value> active);
	void __garbage_collect(map<string,KVS_Value> active);
	vector<void*> classes;
    int kvs_index;
	int autokey = 0;
};



#endif /* KVS_H_ */
