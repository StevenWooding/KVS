<?php

namespace slite\lib\php;

/**
 *
 * @author j. bradley briggs
 */
class KVS
{
	const VALUE_START = "=";
	const VALUE_END = ";";
	const STRUCT_OPEN = "[";
	const STRUCT_CLOSE = "]";
	const META = "~";

	// to string bitmask contants
	const SLITE_FORMAT_PRETTY = 1 ;
	const SLITE_FORMAT_FORCE_KEY = 2 ;
	
	private $expression = ""; // contains the raw string to be parsed
	private $pointer = -1; // position in parsing loop, depth 0
	private $map = [];
	private $incKeyCount = -1;
	
	function __construct($string="") 
	{
		$this->fromString($string);
	}
	
	function printArray($array)
	{
		$result = "<br/><pre>";
		$result .= print_r($array, true);
		$result .= "</pre>";
		return $result;
	}
	
	function toJson()
	{
		return json_encode($this->map);
	}

	function fromJson($jsonString)
	{
		$this->map = json_decode($jsonString);
	}
	
	function fromString($string="")
	{
		if($string)
		{
			$this->expression = $string;
			$this->map = $this->parse();
		}		
	}

	/**
	 * Returns an associative map
	 */	
	private function parse()
	{
		$result = [] ;
		$chars = str_split($this->expression, 1) ; // get char array
		$readKey = true ; //assume we will always be reading a key at the start
		$key = "" ; //captured key
		$val = "" ; // captured value
		$meta = "" ; // captured meta data
		$blank = -1 ; // this will be used if blank keys are encountered
		$hasMeta = false ; // assume no meta data
		while ($this->pointer < count($chars)-1)
		{
			$this->pointer++ ;
			$c = $chars[$this->pointer] ;
			if ($this->pointer != count($chars)-1) $c_ = $chars[$this->pointer+1] ;
			else $c_ = "" ;
			
			// initialisation
				if ($c == self::VALUE_START && $readKey) /// We have the key (and meta if supplied) and this is not an "=" in a value 
				{
						$readKey = false ;
						$key = trim($key) ;
						$blank++ ;
						if ($key == "") // check for a blank key, if so make it an incremental key in the object
						{
							//$blank++ ;
							$key = $blank ;
						}
						if ($meta != "")
						{
							// do something with meta here (or possibly below in next else block when value has been read).
							//echo $meta."<br/>" ;
						}
						continue ;
				}
				else if ($c == self::VALUE_END) // we have the value
				{
					if ($c_ != self::VALUE_END) // if the semi-colon is not escaped
					{
						$readKey = true ;
						$result[$key] = $val ;
						//echo $key." - ".$val."<br>" ;
						$key = "" ;
						$meta = "" ;
						$hasMeta = false ;
						$val = "" ;
						continue ;
					}
					else // if the semi-colon is escaped: advance past it and capture ONE of them before continuing
					{
						$this->pointer++ ;
						$val .= self::VALUE_END ;
						continue ;
					}
				}
				else if ($c == self::STRUCT_OPEN && $readKey) // start of structure but NOT in a value
				{
					$blank++ ;
					$key = trim($key) ;
					if ($key == "") // check for a blank key, if so make it an incremental key in the object
					{
						//$blank++ ;
						$key = $blank ;
					}
					$innerStruct = $this->parse() ; // recurse one level down
					$readKey = true ; // assume we will be reading the key AFTER exiting a structure
					$result[$key] = $innerStruct ; // add this new structure into result
					$key = "" ;
					$meta = "" ;
					$hasMeta = false ;
					$val = "" ;
					continue ;
				}
				else if ($c == self::STRUCT_CLOSE && $readKey) // end of structure and not a square parenthesis in a value
				{
					return $result ;
				}
			
			// capture characters and append either key or value based on readKey state
				if ($readKey) 
				{
					if ($c == self::META) $hasMeta = true ;
					if ($hasMeta == false) $key .= $c ; // capture key name
					else if ($c != self::META) $meta .= $c ; // capture meta 
				}
				else $val .= $c ; //capture value	
		}
		return $result ;
	}

	/**
	 * Gets the value at a particular keyPath.
	 */
	function get(...$keyPath)
	{
		$val = &$this->map;
		foreach ($keyPath as $key)
		{
			if (is_array($val) && array_key_exists($key, $val))
				$val = &$val[$key];
			else return "";
		}
		return $val;
	}

	/**
	 * Returns a kvs structure at the specified key path, if the key path does
	 * not exist, a kvs structure will be created and then returned.
	 */
	function &getKvs(...$keyPath)
	{
		$val = &$this->map;
		foreach ($keyPath as $key)
		{
			if (is_array($val) && array_key_exists($key, $val))
				$val = &$val[$key];
			else // create if no key found
			{
				$val[$key] = [];
				$val = &$val[$key];
			}
		}
		
		$kvs = new KVS();
		$kvs->map=&$val;
		return $kvs;
	}

	static function toNative($kvsString)
	{
		$kvs = new KVS($kvsString);
		return $kvs->getNative();
	}

	static function fromNative($map)
	{
		$kvs = new KVS();
		$kvs->map = $map;
		return $kvs->toString();
	}

	/**
	 * Returns the MAP at the key path instead of the kvs structure, if the key path
	 * does not exist, one will be created. Compare with "getKvs" function.
	 */
	function &getNative(...$keyPath)
	{
		$val = &$this->map;
		foreach ($keyPath as $key)
		{
			if (is_array($val) && array_key_exists($key, $val))
				$val = &$val[$key];
			else // create if no key found
			{
				$val[$key] = [];
				$val = &$val[$key];
			}
		}
		
		return $val;
	}

	/**
	 * Sets a value at the specified key path. The key path need not exist, it will be 
	 * created along the way
	 */
	function set($value, ...$keyPath)
	{
		if(sizeof($keyPath)>0)
		{
			$obj = &$this->map ;
			/// move through the key path, create keys that DON'T exist, leave ones that DO exist intact
			$keyCount = 0 ;
			foreach ($keyPath as $key)
			{
				$keyCount++ ;
				if ($keyCount != count($keyPath)) // if this is not the last key in the path
				{
					if (key_exists($key, $obj)) // if key DOES exist, move into this object
					{
						$obj = &$obj[$key] ; // move into this object
					}
					else // create this key-value in the object
					{
						$obj[$key] = [] ; //create a new array
						$obj = &$obj[$key] ; // move into this object
					}
				}
				else // if this is the last key, inject the value and return
				{
					$obj[$key] = $value ;
				}
			}
		}
		else if(is_array($value))
		{
			$this->map = &$value;
		}
		return $this;
	}

	/**
	 * Removes all values in the kvs structure
	 */
	function clear()
	{
		$this->expression = "" ;
		$this->map = [] ;
	}

	/**
	 * Gets the first key in the kvs structure
	 */
	function getFirstKey()
	{
		$keys = $this->keys() ;
		if (count($keys) == 0) return "" ;
		else return $keys[0] ;
	}

	function getFirstValue()
	{
		$firstKey = $this->getFirstKey() ;
		if ($firstKey == "") return "" ;
		else return $this->map[$firstKey] ;
	}

	static function mergeMaps($mapOld, $mapNew)
	{
		foreach ($mapNew as $key => $value)
		{
			if (key_exists($key, $mapOld) && is_array($value)) //if there is a conflicting key in both maps, and that key refers to a sub-structure, recursively merge:
			{
				$mapNewNew = &$mapNew[$key] ; 
				$mapOldOld = &$mapOld[$key] ; 
				$mapOld[$key] = kvs::mergeMaps($mapOldOld, $mapNewNew) ;
			}
			else $mapOld[$key] = $value ;
		}
		return $mapOld ;
	}

	/**
	 * Merge a kvs structure with this one. Note* conflicting keys are resolved in the following way:
	 * 1.) If the conflicting key refers to a structure, those structures and any sub-structures will be recursively merged. 
	 * 2.) If the conflicting key refers to a simple value, the SUPPLIED kvs is prioritised.
	 */
	function merge(KVS $kvs)
	{
		//echo $this->printArray($this->map) ;
		//echo $this->printArray($kvs->map) ;
		$this->map = &kvs::mergeMaps($this->map, $kvs->map) ;
		return $this ;
	}

	function values()
	{
		return $this->map ;
	}

	/**
	 * Checks if the given keypath exists in the structure
	 */
	function exists(...$keyPath)
	{
		$obj = &$this->map ;
		foreach ($keyPath as $key)
		{
			if (!key_exists($key, $obj)) return false ; // return false at any point a key doesn't exist
			else $obj = &$obj[$key] ; // move into this object
		}
		return true ;
	}

	function add($value)
	{
		$this->incKeyCount++;
		return $this->set($value, $this->incKeyCount);
	}

	/**
	 * Removes a value-key pair at a particular key path
	 */
	function remove(...$keyPath)
	{
		/*$result = 'unset($this->map';
		foreach ($keyPath as $key) $result .= "[\"".$key."\"]";
		$result .= ");";
		eval($result);
		//echo $result;
		return $this;*/
		$obj = &$this->map ;
		$keyCount = 0 ;
		foreach ($keyPath as $key)
		{
			$keyCount++ ;
			if (key_exists($key, $obj)) 
			{
				if ($keyCount != count($keyPath)) $obj = &$obj[$key] ;
				else unset($obj[$key]) ;
			}
			else return $this;
		}
	}

	function isEmpty()
	{
		return !isset($this->map);
	}

	private function tabString($tabs)
	{
		$result = "";
		for ($i = 0; $i < $tabs; $i++)
		{
			$result .= "\t";
		}
		return $result;
	}

	private function arrayToKvs($array, $pretty, $forceKeys,$depth)
	{
		$result = "";
		if ($pretty) $nl = "\n";
		else $nl = "";
		// loop through $map
		$count = -1; 
		foreach ($array as $key => $val)
		{
			$count++;
			if (is_array($val)) // if array: recurse
			{
				// check incremental numerical keys as they are unnecessary to include
				if ($key === $count && !$forceKeys) $key = "";

				if ($pretty)
				{
					$result .= $nl.$this->tabString($depth+1).$key.$nl.$this->tabString($depth+1).self::STRUCT_OPEN;
					$result .= $this->arrayToKvs($val, $pretty, $forceKeys, $depth+1);
					$result .= $nl.$this->tabString($depth+1).self::STRUCT_CLOSE;
				}
				else
				{
					$result .= $key.self::STRUCT_OPEN;
					$result .= $this->arrayToKvs($val, $pretty, $forceKeys, $depth+1);
					$result .= self::STRUCT_CLOSE;
				}
			}
			else // in an array
			{
				if ($val == null) $val = "";

				// check incremental numerical keys as they are unnecessary to include
				if ($key === $count && !$forceKeys) $key = "";
				//echo "<br>".$key."<br>";
				// check for semi-colons in the value that need escaping
				if (strpos($val, self::VALUE_END) !== false)
				{
					$val = str_replace(self::VALUE_END, self::VALUE_END.self::VALUE_END, $val);
				}
				// check if value is null, no need to actually write "null"
				if ($pretty)
					$result .= $nl.$this->tabString($depth+1).$key.self::VALUE_START.$val.self::VALUE_END;
				else
				$result .= $key.self::VALUE_START.$val.self::VALUE_END;
			}
		}
		return $result;
	}

	/**
	 * Prints out kvs "unpretty" and "unwrapped"
	 */
	function toKvs()
	{
		return $this->arrayToKvs($this->map, false, false, 0);
	}
	
	/**
	 * Print out the kvs.
	 * int format: a bitmask with the following options/flags:
	 * 			bit 0: if this bit is set, print out the kvs "pretty"
	 * 			bit 1: if this bit is set, force keys to be printed; this includes auto-incremented keys	
	 * boolean wrap: wraps the entire kvs structure in square parentheses
	 */
	function toString($format=0, $wrap = false)
	{
		$pretty = (($format & (1 << (self::SLITE_FORMAT_PRETTY-1))) !==0) ;
		$forceKeys = (($format & (1 << (self::SLITE_FORMAT_FORCE_KEY-1))) !==0) ;

		if ($pretty) $nl = "\n";
		else $nl = "";

		return ($wrap)? self::STRUCT_OPEN.$this->arrayToKvs($this->map, $pretty, $forceKeys, 0).$nl.self::STRUCT_CLOSE :$this->arrayToKvs($this->map, $pretty, $forceKeys, 0);
	}

	/**
	 * Prints out kvs "unpretty" and "unwrapped"
	 */
	function __toString()
	{
		//(string) $result = $this->printArray($this->map);
		return $this->toKvs();
	}
	
	/**
	 * Returns all the keys in the upper-most structure
	 */
	function keys()
	{
		return array_keys($this->map);
	}
}
/*
$kvs = new KVS() ;
$kvs->set("192.112.112.112", "0", "0", "host") ;
$kvs->set("192.112.16.112", "0", "0", "host") ;
echo $kvs->toString(0b00, false) ;*/
//$m1 = array("") ;
//$m1 = self::mergeMaps($m1, $m2) ;

//$toParse = "name~this is garbage=Peter;surname=Woods;bio=I am a very sophisticated person that loves to hike, swim, and ride bike in the forests. My favourite  lines of code is:;;
//car[[make=BMW;model=X3;engine[capacity=2000;cylinders=6;configuration=straight;]][make=VW;model=Polo;engine[capacity=1200;cylinders=4;configuration=straight;]]]pets[[=name;=type;=breed;=size;=weight;][=fluffy;=cat;=housecat;=small;=2kg;][=skittles;=cat;=housecat;=small;=2kg;][=bambi;=dog;=;=medium;=2kg;][=bobo;=hamster;=dwarf;]]
//";
//$toParse = "title=kvs;;example;people[[name=Jason Bradley;surname=Briggs;Age=10000;][name=Julian;surname=Assange;Age=;]]countries[=usa;=uk;=russia;]2d_array[[=0.992;=122;=0.66748;][=0.11992;=1222;=0.166748;]]=;=;=;=;a field=whoah;=;=;blank array[[]]genre=jaz;;;;z;here be yet another array[4=[]5=[name=yes;surname=please;]]";
//echo "<strong>".$toParse."</strong><br/><br/>";
//echo "Parsing:<br/>";

//$before = microtime(true);
//$toParse = "random=990;random_array=[[=667;=112;=3454;=34993;=3434;=3433;=344;=343;=3444;=3444;=9845;=333443;[[=0;=98334;=33734;=0.334438838873763038;]]]=66989598;=4534534;=334444;]";

//$toParse = "name~this is garbage=Peter;surname=Woods;bio=I am a very sophisticated person that loves to hike, swim, and ride bike in the forests. My favourite  lines of code is:;;;
//car[telemetry=yes;[make=BMW;model=X3;engine[capacity=2000;cylinders=6;configuration=straight;]][make=VW;model=Polo;engine[capacity=1200;cylinders=4;configuration=straight;]]]pets[[=name;=type;=breed;=size;=weight;][=fluffy;=cat;=housecat;=small;=2kg;][=skittles;=cat;=housecat;=small;=2kg;][=bambi;=dog;=;=medium;=2kg;][=bobo;=hamster;=dwarf;]]
//";
//$KVS = new kvs($toParse);
//$KVS = new kvs($toParse);
//$after = microtime(true);
//echo "\nTime taken to parse = ".($after - $before)." seconds\n";

//echo "<br>";
//echo "<strong><br>To JSON:</strong><br>";
//echo $KVS->toJson();
//echo "<br>".$KVS->getValue("genre");
//echo "<br>".$KVS->printArray($KVS->getValue("countries"));
//echo "<br>".$KVS->printArray($KVS->getValue("people"));
///echo "<br>".$KVS->printArray($KVS->getValue("people", "0"));
//echo "<br>".$KVS->getValue("title");
//$KVS->getValue("people", "2", "name");
//echo "<br>";
//echo "<strong><br>Build Kvs:</strong><br>";
//$KVS->addValue(true, "people", "1", "Alive");

//$newKvs = $KVS->toKvs();
//echo $newKvs;
//echo "<br><br><strong>".($newKvs == $toParse ? "KVS Comparison: \u{1F600}" : "KVS Comparison: \u{2639}")."</strong><br>";

//$KVS->removeValue("bio", "0", "engine", "configuration");
//$KVS->set("[5, 2, 34, 112, [2, [19, 773, 663, 112], 883]]", "a big-ass array", "here", "it", "is", "just","wait", "a", "little", "bit", "longer");
//$KVS->remove("pets", "4")->remove("pets", "2")->remove("pets", "3", "2");
//$KVS->set("Piet", "Nickname")->set("30", "Age")->remove("pets")->set("Here you go", "More", "and more", "and more");

//echo $KVS;
//echo "<br>"; 
//echo $KVS->toString(true, true)."\n";
//echo "\n".print_r($KVS->keys());
//$subKVS = new kvs("");
//$subKVS = $KVS->getKvs("pets");
//$subKVS->add("WHAT!");
//$subKVS->add("WHAT!");
//$subKVS->add("WHAT!");
//$subKVS->add("WHAT!");
//$subKVS->remove("3");
//echo "\nnew kvs = \n".$subKVS->toString(true, true);

?>