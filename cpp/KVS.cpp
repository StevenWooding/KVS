/*
 * KVS.cpp
 *
 *  Created on: 13 Feb 2019
 *      Author: hansen
 */

#include "KVS.h"

#define KVS_KEY_TERMINATOR    '='
#define KVS_VALUE_TERMINATOR  ';'
#define KVS_STRUCT_START      '['
#define KVS_STRUCT_END        ']'
#define KVS_META_DATA         '~'

#define KVS_STATE_KEY    0
#define KVS_STATE_VALUE  1
#define KVS_STATE_META   2

KVS::KVS(){ kvs_index = 0; }
KVS::KVS(const KVS& kvs) { this->dictionary = kvs.dictionary; kvs_index = 0;}
KVS::KVS(string kvs, int index){
	kvs_index = index;
	fromString(kvs, kvs_index);
}

KVS::~KVS(){}

void KVS::fromString(string kvs, int &kvs_index){
	int array_counter = 0;
	std::string key_string = "";
	std::string value_string = "";
	int kvs_state = KVS_STATE_KEY;
	while(kvs_index < kvs.length()){
		char currentChar = kvs.at(kvs_index);
		if(currentChar == KVS_KEY_TERMINATOR){
			if(kvs_state == KVS_STATE_KEY)
				kvs_state = KVS_STATE_VALUE;
			else if(kvs_state == KVS_STATE_META)
				kvs_state = KVS_STATE_VALUE;
			else if(kvs_state == KVS_STATE_VALUE)
				value_string += currentChar;
		}else if(currentChar == KVS_META_DATA){
			kvs_state = KVS_STATE_META;
		}else if(currentChar == KVS_VALUE_TERMINATOR){
			if(kvs_index+1 < kvs.length()){
				if(currentChar == kvs.at(kvs_index+1)){
					kvs_index++;
					value_string += currentChar;
				}else{
					kvs_state = KVS_STATE_KEY;
					trim(key_string);
					if(key_string.empty())
						key_string = to_string(array_counter++);
					dictionary[key_string] = KVS_Value(KVS_VALUE_STRING, (void*)value_string.c_str(), value_string.length());
					key_string = "";
					value_string = "";
				}
			}else{
				kvs_state = KVS_STATE_KEY;
				trim(key_string);
				if(key_string.empty())
					key_string = to_string(array_counter++);
				dictionary[key_string] = KVS_Value(KVS_VALUE_STRING, (void*)value_string.c_str(), value_string.length());
				key_string = "";
				value_string = "";
			}
		}else if(currentChar == KVS_STRUCT_START){
			kvs_state = KVS_STATE_KEY;
			kvs_index++;
			trim(key_string);
			if(key_string.empty())
				key_string = to_string(array_counter++);
			dictionary[key_string] = KVS_Value(KVS_VALUE_STRUCT, (void*)new KVS(kvs, kvs_index), sizeof(KVS));
			kvs_index += ((KVS*)dictionary[key_string].value)->kvs_index - kvs_index;
			key_string = "";
			value_string = "";
		}else if(currentChar == KVS_STRUCT_END){
			if(kvs_state == KVS_STATE_KEY)
				return;
		}else{
			if(kvs_state == KVS_STATE_KEY)
				key_string += currentChar;
			else if(kvs_state == KVS_STATE_VALUE)
				value_string += currentChar;
		}

		kvs_index++;
	}
	for(map<string,KVS_Value>::iterator it = dictionary.begin(); it != dictionary.end(); ++it){
			keys.push_back(it->first);
			values.push_back(it->second);
	}

}

string KVS::__dict_to_kvs(map<string,KVS_Value> active){
	string returnString = "";
	for(map<string,KVS_Value>::iterator it = active.begin(); it != active.end(); ++it) {
		if(it->second.type == KVS_VALUE_STRING){
			returnString.append(it->first);
			returnString.append("=");
			string value = (char*)it->second.value;
			value = do_replace(value, ";", ";;");
			returnString.append(value);
			returnString.append(";");
		}else{
			returnString.append(it->first);
			returnString.append("[");
			returnString.append(__dict_to_kvs(((KVS*)it->second.value)->dictionary));
			returnString.append("]");
		}

	}
	return returnString;
}

string KVS::toString(){
	return __dict_to_kvs(dictionary);
}

void KVS::__garbage_collect(map<string,KVS_Value> active){
	for(map<string,KVS_Value>::iterator it = active.begin(); it != active.end(); ++it) {
		if(it->second.type == KVS_VALUE_STRING)
			classes.push_back(it->second.value);
		else{
			classes.push_back(it->second.value);
			__garbage_collect(((KVS*)active[it->first].value)->dictionary);
		}
	}
}

void KVS::Add(KVS_Value value){
	while(true){
		if(find(keys.begin(), keys.end(), to_string(autokey)) != keys.end()){
			autokey++;
		}else{
			dictionary[to_string(autokey++)] = value;
			return;
		}
	}
}

void KVS::Clear(){
	__garbage_collect(dictionary);
	for(int i = 0; i < classes.size(); i++)
		free(classes.at(i));
	dictionary.clear();
}

void KVS::Debug(map<string,KVS_Value> active){
	for(map<string,KVS_Value>::iterator it = active.begin(); it != active.end(); ++it) {
		if(it->second.type == KVS_VALUE_STRING)
			cout << it->first << " = " << (char*)it->second.value << "\n";
		else{
			cout << it->first << "\n";
			Debug(((KVS*)active[it->first].value)->dictionary);
		}
	}
	cout << "\n";
}

bool KVS::Exists(int count, ...){
	bool returnBool = false;
	va_list args;
	va_start(args, count);
	vector<string> active = keys;
	for(int i = 0; i < count; i++){
		string currentKey = string(va_arg(args, char*));
		cout << i << " " << currentKey << " " << "\n";
//		if(find(active.begin(), active.end(), string(currentKey)) != keys.end()){
			for(int j = 0; j < active.size(); j++)
				cout << "LOOKING FOR " << currentKey << " : " << active.at(j) << "\n";
//			if(dictionary[string(currentKey)].type == KVS_VALUE_STRUCT){
//				cout << "FOUND\n";
//				active = ((KVS*)dictionary[string(currentKey)].value)->keys;
//			}
//
//		}else{
//			cout << "NOT FOUND\n";
//			returnBool = false;
//		}
		for(int j = 0; j < active.size(); j++){
			if(currentKey == active.at(j)){
				if(dictionary[currentKey].type == KVS_VALUE_STRUCT){
					active = ((KVS*)dictionary[currentKey].value)->keys;
					returnBool = true;
					break;
				}else if(i == count-1){
					returnBool = true;
					break;
				}
			}else{
				returnBool = false;
			}
		}
	}
	va_end(args);
	return returnBool;
}
