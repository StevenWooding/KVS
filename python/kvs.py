import collections
import json
import copy

KVS_KEY_TERMINATOR = '='
KVS_VALUE_TERMINATOR = ';'
KVS_STRUCT_START = '['
KVS_STRUCT_END = ']'
KVS_META_DATA = '~'

KVS_STATE_KEY = 0
KVS_STATE_VALUE = 1
KVS_STATE_META = 2


class INT(object):
    encoding = 'UTF-8'

    def __init__(self, init_value):
        self.value = init_value


class KVS(object):
    encoding = 'UTF-8'

    def __init__(self,  kvs=None, index=None):
        self.kvs = collections.OrderedDict()
        self.index = INT(0)
        self.depth = INT(1)
        self.default_key = INT(0)
        if isinstance(kvs, str):
            if index is None:
                self.index.value = 0
                self.__parse_kvs(kvs, self.index)
            else:
                self.__parse_kvs(kvs, index)
        elif isinstance(kvs, KVS):
            self.kvs = kvs.kvs

    def __dict_to_kvs(self, kvs, pretty, wrap):
        return_string = ''
        if wrap is not None:
            return_string += wrap
            return_string += '['
            if pretty is True:
                return_string += "\n\t"
        for key, value in kvs.items():
            if pretty is True:
                return_string += "\n"
                for i in range(0, self.depth.value):
                    return_string += "\t\t"
            return_string += key
            if isinstance(value, KVS):
                return_string += '['
                if pretty is True:
                    for i in range(0, self.depth.value):
                        return_string += "\t"
                    self.depth.value += 1
                return_string += self.__dict_to_kvs(value.kvs, pretty, None)
                if pretty is True:
                    return_string += "\n"
                    for i in range(0, self.depth.value):
                        return_string += "\t\t"
                    self.depth.value -= 1
                return_string += ']'
            else:
                return_string += '='
                if ';' in value:
                    new_string = ''
                    for c in value:
                        if c is ';':
                            new_string += ';'
                        new_string += c
                    return_string += new_string
                else:
                    return_string += value
                return_string += ';'
                if pretty is True:
                    return_string += "\t\t"
        if wrap is not None:
            return_string += ']'
        return return_string

    def __dump(self, obj):
        newobj = copy.deepcopy(obj)
        for key, value in newobj.items():
            if isinstance(value, KVS):
                tmpObj = self.__dump(newobj[key].kvs)
                newobj[key] = tmpObj
        return newobj

    def __parse_kvs(self, kvsString, index):
        array_counter = 0
        kvs_key_string = ''
        kvs_value_string = ''
        kvs_state = KVS_STATE_KEY
        while index.value < len(kvsString):
            currentChar = kvsString[index.value]
            if currentChar is KVS_KEY_TERMINATOR:
                if kvs_state == KVS_STATE_KEY:
                    kvs_state = KVS_STATE_VALUE
                elif kvs_state == KVS_STATE_META:
                    kvs_state = KVS_STATE_VALUE
                elif kvs_state == KVS_STATE_VALUE:
                    kvs_value_string += currentChar

            elif currentChar is KVS_META_DATA:
                kvs_state = KVS_STATE_META
            elif currentChar is KVS_VALUE_TERMINATOR:
                if index.value+1 < len(kvsString):
                    if currentChar is kvsString[index.value+1]:
                        index.value += 1
                        kvs_value_string += currentChar
                    else:
                        kvs_state = KVS_STATE_KEY
                        if kvs_key_string.strip() is '':
                            kvs_key_string = str(array_counter)
                            array_counter += 1
                        self.kvs[kvs_key_string.strip()] = kvs_value_string
                        kvs_key_string = ''
                        kvs_value_string = ''
                else:
                    kvs_state = KVS_STATE_KEY
                    if kvs_key_string.strip() is '':
                        kvs_key_string = str(array_counter)
                        array_counter += 1
                    self.kvs[kvs_key_string.strip()] = kvs_value_string
                    kvs_key_string = ''
                    kvs_value_string = ''
            elif currentChar is KVS_STRUCT_START:
                kvs_state = KVS_STATE_KEY
                index.value+=1
                if kvs_key_string.strip() is '':
                    kvs_key_string = str(array_counter)
                    array_counter += 1
                self.kvs[kvs_key_string.strip()] = KVS(kvsString, index)
                kvs_key_string = ''
                kvs_value_string = ''
            elif currentChar is KVS_STRUCT_END:
                return
            else:
                if kvs_state == KVS_STATE_KEY:
                    kvs_key_string += currentChar
                elif kvs_state == KVS_STATE_VALUE:
                    kvs_value_string += currentChar
            index.value += 1

    def add(self, value):
        while True:
            if str(self.default_key.value) in self.kvs.keys():
                self.default_key.value += 1
            else:
                self.kvs[str(self.default_key.value)] = value
                self.default_key.value += 1
                return

    def clear(self):
        self.kvs.clear()

    def exists(self, *argv):
        activeObject = copy.deepcopy(self.kvs)
        for arg in argv:
            if arg in activeObject.keys():
                if isinstance(activeObject[arg], KVS):
                    activeObject = activeObject[arg].kvs
                else:
                    return True
            else:
                return False

    def fromString(self, kvsString):
        self.__parse_kvs(kvsString, self.index)

    def get(self, *argv):
        activeObject = copy.deepcopy(self.kvs)
        for arg in argv:
            if arg in activeObject.keys():
                if isinstance(activeObject[arg], KVS):
                    activeObject = activeObject[arg].kvs
                else:
                    return activeObject[arg]
            else:
                return ""
        return ""

    def getFirstKey(self):
        if self.kvs.keys():
            return list(self.kvs.keys())[0]
        else:
            return ""

    def getFirstValue(self):
        if self.kvs.values():
            return list(self.kvs.values())[0]
        else:
            return ""

    def getKVS(self, *argv):
        activeObject = self
        for arg in argv:
            if arg in activeObject.kvs.keys():
                if isinstance(activeObject.kvs[arg], KVS):
                    activeObject = activeObject.kvs[arg]
                else:
                    return ""
            else:
                activeObject.kvs[arg] = KVS()
                activeObject = activeObject.kvs[arg]
        return activeObject

    def getNative(self, *argv):
        activeObject = self
        for arg in argv:
            if arg in activeObject.kvs.keys():
                if isinstance(activeObject.kvs[arg], KVS):
                    activeObject = activeObject.kvs[arg]
                else:
                    return ""
            else:
                activeObject.kvs[arg] = KVS()
                activeObject = activeObject.kvs[arg]
        return activeObject.kvs

    def keys(self):
        return list(self.kvs.keys())

    def merge(self, kvs, sub=None):
        baseKVS = self
        if sub is not None:
            baseKVS = sub
        for key in kvs.kvs.keys():
            if isinstance(kvs.kvs[key], KVS):
                self.merge(kvs.kvs[key], baseKVS.kvs[key])
            else:
                baseKVS.kvs[key] = kvs.kvs[key]

    def remove(self, *argv):
        activeObject = self.kvs
        for arg in argv:
            if arg in activeObject.keys():
                if isinstance(activeObject[arg], KVS):
                    activeObject = activeObject[arg].kvs
                else:
                    activeObject.pop(arg, None)

    def set(self, value, *argv):
        activeObject = self.kvs
        for i in range(0, len(argv)):
            if argv[i] in activeObject.keys():
                if isinstance(activeObject[argv[i]], KVS):
                    if i < len(argv)-1:
                        activeObject = activeObject[argv[i]].kvs
            else:
                if i < len(argv) - 1:
                    activeObject[argv[i]] = KVS()
                    activeObject = activeObject[argv[i]].kvs
        activeObject[argv[i]] = value

    def toString(self, pretty=False, wrap=None):
        return self.__dict_to_kvs(self.kvs, pretty, wrap)

    def values(self):
        return list(self.kvs.values())

    def debug(self):
        print(json.dumps(self.__dump(self.kvs), indent=4, default=str))
