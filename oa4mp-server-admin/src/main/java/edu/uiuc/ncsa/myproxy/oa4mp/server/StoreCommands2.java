package edu.uiuc.ncsa.myproxy.oa4mp.server;

import edu.uiuc.ncsa.security.core.Identifiable;
import edu.uiuc.ncsa.security.core.Store;
import edu.uiuc.ncsa.security.core.XMLConverter;
import edu.uiuc.ncsa.security.core.util.MyLoggingFacade;
import edu.uiuc.ncsa.security.core.util.StringUtils;
import edu.uiuc.ncsa.security.storage.XMLMap;
import edu.uiuc.ncsa.security.storage.data.MapConverter;
import edu.uiuc.ncsa.security.storage.data.SerializationKeys;
import edu.uiuc.ncsa.security.util.cli.*;
import net.sf.json.*;

import java.io.*;
import java.util.*;

import static edu.uiuc.ncsa.security.core.util.StringUtils.*;
import static edu.uiuc.ncsa.security.util.cli.CLIDriver.CLEAR_BUFFER_COMMAND;
import static edu.uiuc.ncsa.security.util.cli.CLIDriver.EXIT_COMMAND;

/**
 * This class exists because we cannot quite get the dependencies right otherwise. Mostly it is to have access
 * to converters for de/serialization and searching
 * <p>Created by Jeff Gaynor<br>
 * on 7/2/18 at  10:06 AM
 */
public abstract class StoreCommands2 extends StoreCommands {
    public StoreCommands2(MyLoggingFacade logger, String defaultIndent, Store store) {
        super(logger, defaultIndent, store);
    }

    public StoreCommands2(MyLoggingFacade logger, Store store) {
        super(logger, store);
    }

    /**
     * Get the {@link MapConverter} for the store.
     *
     * @return
     */

    @Override
    public void deserialize(InputLine inputLine) {
        if (showHelp(inputLine)) {
            showDeserializeHelp();
            return;
        }
        InputStream is;
        boolean isNew = inputLine.hasArg("-new");
        if (inputLine.hasArg("-file")) {
            try {
                is = new FileInputStream(inputLine.getNextArgFor("-file"));

                XMLMap map = new XMLMap();
                map.fromXML(is);
                is.close();
                Identifiable x = getStore().getXMLConverter().fromMap(map, null);
                if (isNew) {
                    if (getStore().containsKey(x.getIdentifier())) {
                        say("Error! The object with identifier \"" + x.getIdentifierString() + "\" already exists and you specified the item was new. Aborting.");
                        return;
                    }
                } else {
                    if (x.getIdentifier() == null) {
                        //handles the case where this is new and needs an identifier created. Only way to get
                        // a new unused identifier reliably is to have the store create a new entry then we update that.
                        Identifiable c = getStore().create();
                        x.setIdentifier(c.getIdentifier());
                        say("Created identifier \"" + c.getIdentifierString() + "\".");
                    }
                    // second case, overwrite whatever.
                    getStore().save(x);
                }
                say("done!");
            } catch (Throwable e) {
                say("warning, load file at path \"" + inputLine.getNextArgFor("-file") + "\": " + e.getMessage());
            }
        } else {
            say("Missing file argument. Cannot deserialize.");
            return;
        }

    }

    @Override
    protected int longFormat(Identifiable identifiable) {
        return longFormat(identifiable, false);

    }

    protected int longFormat(Identifiable identifiable, boolean isVerbose) {
        XMLMap map = new XMLMap();
        getStore().getXMLConverter().toMap(identifiable, map);

        TreeMap<String, Object> tMap = new TreeMap<>();
        tMap.putAll(map);
        int width = 0;
        for (String key : tMap.keySet()) {
            width = Math.max(width, key.length());
        }

        // Use the order of the tmap (so its sorted) but the XMLMap has information we need to get these.
        for (String key : tMap.keySet()) {
            String v = map.getString(key);
            if(!StringUtils.isTrivial(v)) {
                try {
                    JSON json = JSONSerializer.toJSON(v);
                    v = json.toString(1);
                } catch (Throwable t) {

                }
                say(formatLongLine(key, v, width, isVerbose));
            }

        }
        return width;
    }

    int display_width = 120;

    /**
     * Gets a consistent look and feel. If you have to override {@link #longFormat(Identifiable)}
     * and add your own entries, use this.
     *
     * @param leftSide
     * @param rightSide
     * @param leftColumWidth
     * @return
     */
    protected String formatLongLine(String leftSide, String rightSide, int leftColumWidth, boolean isVerbose) {
        int dd = indentWidth() + 3; // the default indent plus the " : " in the middle
        int realWidth = display_width - dd;
        boolean shortLine = rightSide.length() + leftColumWidth + 1 <= realWidth;
        if (isVerbose) {

            List<String> flowedtext = StringUtils.wrap(0, StringUtils.toList(rightSide), realWidth - leftColumWidth);

            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append(RJustify(leftSide, leftColumWidth) + " : " + flowedtext.get(0) + ((flowedtext.size() <= 1 && shortLine)?"":"\n"));
            boolean isFirstLine = true;
            for (int i = 1; i < flowedtext.size(); i++) {
                if (isFirstLine) {
                    isFirstLine = false;
                    stringBuffer.append(StringUtils.getBlanks(dd + leftColumWidth) + flowedtext.get(i));
                } else {
                    stringBuffer.append("\n" + StringUtils.getBlanks(dd + leftColumWidth) + flowedtext.get(i));
                }
            }
            return stringBuffer.toString();

        }
        return RJustify(leftSide, leftColumWidth) + " : " + truncate(rightSide.replace("\n","").replace("\r",""));
    }

    @Override
    public void serialize(InputLine inputLine) {
        if (showHelp(inputLine)) {
            showSerializeHelp();
            return;
        }
        OutputStream os = System.out;
        boolean hasFile = false;
        if (inputLine.hasArg("-file")) {
            try {
                os = new FileOutputStream(inputLine.getNextArgFor("-file"));
                hasFile = true;
            } catch (FileNotFoundException e) {
                say("warning, could not find file in argument \"" + inputLine.getNextArgFor("-file"));
            }
            inputLine.removeSwitchAndValue("-file");
        }

        Identifiable x = findItem(inputLine);
        if (x == null) {
            say("Object not found");
            return;
        }
        XMLMap c = new XMLMap();
        getStore().getXMLConverter().toMap(x, c);

        try {
            c.toXML(os);
            if (hasFile) {
                os.flush();
                os.close();
            }
            say("done!");
        } catch (IOException e) {
            say("Error serializing object.");
        }
    }

    protected void showSearchHelp() {
        say("search -key key [-regex|-la -size] [-listKeys] condition");
        sayi("Searches the current component for all entries satisfying the condition. You may also specify that the ");
        sayi("condition is a regular expression rather than using simple equality");
        sayi("Invoking this with the -listkeys flag prints out all the keys for this store. Omit that for searches.");
        sayi("-key = the name of the key to be searched for");
        sayi("-regex (optional) attempt to interpret the conditional as a regular expression");
        sayi("-la (optional) print the result in long format.");
        sayi("-size (optional) print *only* the number of results.");
        sayi("\nE.g.\n");
        sayi("search -key client_id -regex \".*07028.*\"");
        sayi("\n(In the clients components) This would find the clients whose identifiers contain the string 07028");
        sayi("\nE.g.\n");
        sayi("search -key email -regex \".*bigstate\\.edu.*\"");
        sayi("\n(in the clients or user component) This would match all email addresses from that institution bigstate.edu. \n");
        sayi("Note that the period must be escaped for a regex.");

    }


    @Override
    public void search(InputLine inputLine) {
        String KEY_FLAG = "-key";
        String LIST_KEYS_FLAG = "-listKeys";
        String REGEX_FLAG = "-regex";
        String LONG_LIST_FLAG = "-la";
        String SIZE_FLAG = "-size";
        if (showHelp(inputLine)) {
            showSearchHelp();
            return;
        }
        SerializationKeys keys = ((MapConverter) getStore().getXMLConverter()).getKeys();

        if (inputLine.hasArg(LIST_KEYS_FLAG)) {
            if (getStore().getXMLConverter() instanceof MapConverter) {
                say("keys");
                say("-------");
                for (String key : keys.allKeys()) {
                    say(key);
                }
            }
            return;
        }

        if (inputLine.hasArg(KEY_FLAG)) {
            String key = inputLine.getNextArgFor(KEY_FLAG);
            if (!keys.allKeys().contains(key)) {
                say("Sorry, but the key \"" + key + "\" is not a known key for this component.");
                return;
            }
            List<Identifiable> values = null;
            try {
                values = getStore().search(key, inputLine.getLastArg(), inputLine.hasArg(REGEX_FLAG));
            } catch (Throwable t) {
                say("Sorry, that didn't work:" + t.getMessage());
                return;
            }
            if (values.isEmpty()) {
                say("no matches");
            }
            if (inputLine.hasArg(SIZE_FLAG)) {
                say("Got " + values.size() + " results");
                return;
            }
            for (Identifiable identifiable : values) {
                if (inputLine.hasArg(LONG_LIST_FLAG)) {
                    longFormat(identifiable);
                } else {
                    say(format(identifiable));
                }
            }
            say("\ngot " + values.size() + " match" + (values.size() == 1 ? "." : "es."));
        } else {
            say("Sorry, you must specify a key for the search. Try typing \nsearch " + LIST_KEYS_FLAG + "\n for all available keys");
        }

    }

    @Override
    protected void showLSHelp() {
        say("ls [" + LINE_LIST_COMMAND + "  | " + VERBOSE_COMMAND + " | " + ALL_LIST_COMMAND + "] | [" + KEY_FLAG + " key | " + KEYS_FLAG + " array] id");
        sayi("Lists information about the contents of the store, an entry and individual values of the entry.");
        sayi("When listing multiple entries, tools will use the most numbers from the most recent call to this.");
        sayi("A line listing is tabular and will shorten entries that are too long, ending them with " + ELLIPSIS);
        sayi("A verbose command will format every bit of every entry within the margins.");
        say("E.g.");
        sayi("ls " + LINE_LIST_COMMAND + "  " + ALL_LIST_COMMAND);
        sayi("Prints out the line form of *every* object in this store. This may be simply huge");
        say("E.g.");
        sayi("ls");
        sayi("Prints out the short form of *every* object in this store. This may also be huge.");
        sayi("If you are using this to find things, you probably want to look at the search command");
        say("E.g.");
        sayi("ls " + LINE_LIST_COMMAND + "  /foo:bar");
        sayi("Prints a line format for the entry with id foo:bar");
        say("E.g.");
        sayi("ls " + VERBOSE_COMMAND + " /foo:bar");
        sayi("prints out a verbose listing of the entry with id foo:bar.");
        say("E.g.");
        sayi("ls " + KEY_FLAG + " id /foo:bar");
        sayi(">   foo:bar");
        sayi("Prints out the id property for the object with identifier foo:bar");
        sayi("");
        sayi("You may also supply a list of keys in an array of the form [key0,key1,...].");
        say("E.g.");
        sayi("ls " + KEYS_FLAG + " [id,callback_uris,create_ts] /foo:bar");
        sayi("would print the id, callback_uri and create_ts properties for the object with id");
        sayi("foo:bar. ");
        sayi("\nSee also list_keys, search");
    }

    @Override
    protected void showRMHelp() {
        say("rm [" + KEY_FLAG + " | " + KEYS_FLAG + " list] id");
        sayi("Remove a property from this the object with the given value.");
        sayi("If you supply a list, all of the properties in the list will be removed");
        sayi("No list of keys means to remove the entire object from the store (!)");
        say("E.g.");
        sayi("rm " + KEY_FLAG + " error_uri /foo:bar");
        sayi("Removes the value of the property 'error_uri' from the object with id foo:bar");
        say("E.g.");
        sayi("rm /foo:bar");
        sayi("removes the object with id foo:bar completely from the store");
        say("E.g.");
        sayi("rm " + KEYS_FLAG + " [error_uri,home_uri] /foo:bar");
        sayi("removes the values of the properties error_uri and home_uri from the object with id");
        sayi("equal to foo:bar");

    }

    /**
     * Called if there is additional clean up needed. For instance, removing a client
     * requires removing its approval record.
     *
     * @param identifiable
     */
    protected void rmCleanup(Identifiable identifiable) {
    }

    @Override
    public void rm(InputLine inputLine) {
        if (showHelp(inputLine)) {
            showRMHelp();
            return;
        }
        Identifiable identifiable = findItem(inputLine);
        if (identifiable == null) {
            say("Object not found");
            return;
        }
        // if the request does not have new stuff, do old stuff.
        if (!inputLine.hasArg(KEY_FLAG) && !inputLine.hasArg(KEYS_FLAG)) {
            super.rm(inputLine);
            rmCleanup(identifiable);
            return;
        }
        if (inputLine.hasArg(KEYS_FLAG)) {
            List<String> array = getKeysList(inputLine);

            if (array == null) {
                say("sorry, but this requires a list for this option.");
                return;
            }
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }
            removeEntries(identifiable, array);
        }
        if (inputLine.hasArg(KEY_FLAG)) {
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }

            removeEntry(identifiable, inputLine.getNextArgFor(KEY_FLAG));
        }
        //    rmCleanup(identifiable);
    }

    @Override
    public void ls(InputLine inputLine) {
        if (showHelp(inputLine)) {
            showLSHelp();
            return;
        }

        if (!inputLine.hasArg(KEY_FLAG) && !inputLine.hasArg(KEYS_FLAG)) {
            super.ls(inputLine);
            return;
        }
        if (inputLine.hasArg(KEYS_FLAG)) {
            List<String> array = getKeysList(inputLine);

            if (array == null) {
                say("sorry, but this requires a list for this option.");
                return;
            }
            Identifiable identifiable = findItem(inputLine);
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }

            showEntries(identifiable, array, inputLine.hasArg(VERBOSE_COMMAND));
            return;
        }
        if (inputLine.hasArg(KEY_FLAG)) {
            Identifiable identifiable = findItem(inputLine);
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }
            showEntry(identifiable, inputLine.getNextArgFor(KEY_FLAG), inputLine.hasArg(VERBOSE_COMMAND));
        }

    }

    @Override
    protected void showUpdateHelp() {
        say("update [" + KEY_FLAG + " key " + VALUE_FLAG + " value | " +
                KEYS_FLAG + " array] index\n");
        sayi("where the index is the index in the list command.");
        sayi("This has three modes. Just an id will prompt you for every value to update.");
        say("Alternately, you may either specify a single key + value OR you may specify an array of keys");
        sayi("of the form [key0,key1,...]. (The list_keys command will tell what the keys are.)");
        sayi("The " + KEYS_FLAG + " will act on all the keys supplied.");
        say("E.g.");
        sayi("update " + KEY_FLAG + " name " + VALUE_FLAG + " \"My client\" /foo:bar");
        sayi("This changes the value of the 'name' attribute to 'My client' for the object with id 'foo:bar'");
        sayi("Note that no prompting is done! The value will be updated.");
        say("E.g.");
        sayi("update " + KEYS_FLAG + " [name,callback_uri] /foo:bar");
        sayi("This would prompt to update the values for the 'name' and 'callback_uri' properties");
        sayi("of the object with id 'foo:bar'");
        sayi("A few notes. If the value of the property is a JSON object, you can edit it.");
        sayi("If the value of the property is an array, then you may add a value, delete a value,");
        sayi("replace the entire contents (new entries are comma separated) or simply clear the .");
        sayi("entire list of entries. You may also back out of the update request.");
        say("See also list_keys");
    }


    String KEY_FLAG = "-key";
    String VALUE_FLAG = "-value";
    String KEYS_FLAG = "-keys";

    @Override
    public void update(InputLine inputLine) {

        if (showHelp(inputLine)) {
            showUpdateHelp();
            return;
        }
        if (!inputLine.hasArg(KEY_FLAG) && !inputLine.hasArg(KEYS_FLAG)) {
            super.update(inputLine);
            return;
        }

        Identifiable identifiable = null;
        XMLMap map = null;
        // Since the value can be anything --like a path to a file. e.g. /tmp/foo or
        // an integer, we *have* to remove arguments until we can see what the
        // actual id is.
        boolean gotOne = false;
        String key = null;
        String value = null;
        if (inputLine.hasArg(KEY_FLAG)) {
            key = inputLine.getNextArgFor(KEY_FLAG);
            if (!hasKey(key)) {
                say("sorry, but \"" + key + "\" is not a recognized attribute.");
                return;
            }
            inputLine.removeSwitchAndValue(KEY_FLAG);
            if (inputLine.hasArg(VALUE_FLAG)) {
                value = inputLine.getNextArgFor(VALUE_FLAG);
                inputLine.removeSwitchAndValue(VALUE_FLAG);
            }
            identifiable = findItem(inputLine);
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }
            map = toXMLMap(identifiable);
            if (value == null) {
                gotOne = updateSingleValue(map, key);
            } else {
                map.put(key, value);
                gotOne = true;
            }
        }
        if (inputLine.hasArg(VALUE_FLAG)) {
            say("Malformed update request. If you specify a value, you must specify a single key.");
            return;
        }


        if (inputLine.hasArg(KEYS_FLAG)) {
            List<String> keys = getKeysList(inputLine);
            inputLine.removeSwitchAndValue(KEYS_FLAG);
            identifiable = findItem(inputLine);
            if (identifiable == null) {
                say("sorry, I could not find that object. Check your id.");
                return;
            }
            map = toXMLMap(identifiable);

            for (String k : keys) {
                gotOne = updateSingleValue(map, k) || gotOne; // order matters!
            }
        }

        if (gotOne) {
            getStore().save(fromXMLMap(map));
        }

    }

    protected JSONArray updateSingleValue(String key, JSONArray currentValue) {
        say("current value=" + currentValue);
        String action = getInput("Add, clear, delete, replace or exit?(a/c/d/r/x)", "a").toLowerCase();
        if (action.equals("x")) {
            return null; // do nothing.
        }
        if (action.equals("r")) {
            say("Enter the new elements with commas between them");
        }
        String newValue = null;
        if (action.equals("d")) {
            newValue = getInput("Value to remove", "");
        } else {
            newValue = getInput("New value", "");
        }
        switch (action) {
            case "a":
                // Append a value to the list
                currentValue.add(newValue);
                return currentValue;
            case "c":
                // clear the entire contents
                currentValue.clear();
                return currentValue;
            case "d":
                // delete a single value in the list
                currentValue.remove(newValue);
                return currentValue;
            case "r":
                // replace the entire contents.
                currentValue.clear();
                if (newValue.equals("")) {
                    return currentValue;
                }
                StringTokenizer st = new StringTokenizer(newValue, ",");
                while (st.hasMoreElements()) {
                    currentValue.add(st.nextToken());
                }
                return currentValue;

        }
        say("sorry, I did not understand what you want to do.");
        return null;
    }

    protected boolean supportsQDL() {
        return false;
    }

    /**
     * The contract is that this gets the entire current config and updates <i>exactly</i>
     * the bits relating to QDL. This is then saved elsewhere.
     *
     * @param currentConfig
     * @return
     */
    protected JSONObject loadQDLScript(JSONObject currentConfig) {
        return currentConfig; // do nothing.
    }

    protected boolean updateSingleValue(XMLMap map, String key) {
        String currentValue = map.getString(key);

        JSON json = null;
        if(currentValue != null) {
            // edge case to avoid  a &^*%@! JSON null object.
            // JSONNull means parsing a null into a JSON object that bombs everyplace like a regular null.,
            // i.e,. every operation throws the equivalent of an NPE.
            // They just do it so they have a typed null of type JSON...
            try {
                json = JSONSerializer.toJSON(currentValue);
            } catch (Throwable t) {
                // ok, it's not JSON
            }
        }

        if (json == null) {
            // This handles every other value type...
            String newValue = getInput("Enter new value for " + key + " ", currentValue);
            if (newValue.equals(currentValue)) {
                return false;
            }
            map.put(key, newValue);
            return true;
        }
        if (json != null   && (json instanceof JSONObject)) {
            if (supportsQDL()) {
                boolean loadQDL = getInput("Load only a QDL script or edit the full config? (q/f)", "f").equalsIgnoreCase("q");
                if (loadQDL) {
                    JSONObject oldCfg = (JSONObject) json;
                    JSONObject qdlcfg = loadQDLScript(oldCfg);

                    if (qdlcfg == null) {
                        return false;
                    } // they cancelled out of it

                    map.put(key, oldCfg.toString());
                    return true;
                } else {
                    JSONObject newConfig = (JSONObject) inputJSON((JSONObject) json, "client configuration");
                    if (newConfig == null) {
                        return false;
                    } // user cancelled
                    map.put(key, newConfig);
                    return true;
                }
            } else {
                JSONObject newJSON = inputJSON((JSONObject) json, key);
                if (newJSON == null) {
                    return false;
                } // user cancelled
                map.put(key, newJSON);
                return true;
            }
        }
        if (json != null && (json instanceof JSONArray)) {
            JSONArray newArray = updateSingleValue(key, (JSONArray) json);
            // really hard to tell if the array is updated in the general case.
            // so just always save it.
            if (newArray == null) {
                return false;
            }
            map.put(key, newArray);
            return true;
        }
        return false; // Just in case, do nothing.
    }


    /**
     * Allows for entering a new JSON object. This permits multi-line entry so formatted JSON can be cut and pasted
     * into the command line (as long as there are no blank lines). This will validate the JSON, print out a message and
     * check that you want to keep the new JSON. Note that you cannot overwrite the value of a configuration at this point
     * mostly as a safety feature. So hitting return or /exit will have the same effect of keeping the current value.
     *
     * @param oldJSON
     * @return null if the input is terminated (so retain the old object)
     */
    protected JSONObject inputJSON(JSONObject oldJSON, String key) {
        if (oldJSON == null) {
            sayi("no current value for " + key);
        } else {
            sayi("current value for " + key + ":");
            say(oldJSON.toString(1));
        }
        sayi("Enter new JSON value. An empty line terminates input. Entering a line with " + EXIT_COMMAND + " will terminate input too.\n Hitting " + CLEAR_BUFFER_COMMAND + " will clear the contents of this.");
        String rawJSON = "";
        boolean redo = true;
        while (redo) {
            try {
                String inLine = readline();
                while (!isEmpty(inLine)) {
                    if (inLine.equals(CLEAR_BUFFER_COMMAND)) {
                        return new JSONObject();
                    }
                    rawJSON = rawJSON + inLine;
                    inLine = readline();
                }
            } catch (ExitException x) {
                // ok, so user terminated input. This ends the whole thing
                return null;
            }
            // if the user just hits return with no input, do nothing. This lets them skip over unchanged entries.
            if (rawJSON.isEmpty()) {
                return null;
            }
            try {
                JSONObject json = null;
                json = JSONObject.fromObject(rawJSON);
                sayi("Success! JSON is valid.");
                return json;
            } catch (Throwable t) {
                sayi("uh-oh... It seems this was not a valid JSON object. The parser message reads:\"" + t.getMessage() + "\"");
                redo = isOk(getInput("Try to re-enter this?", "true"));
            }
        }

        return null;
    }

    /**
     * Once an object is found in the store, convert it to JSON so that the properties may be
     * accessed in a canonical way. This lets us take any identifiable object and manipulate its
     * properties without knowing anything else about it.
     *
     * @param identifiable
     * @return
     */
    protected XMLMap toXMLMap(Identifiable identifiable) {
        Identifiable x = (Identifiable) getStore().get(identifiable.getIdentifier());
        XMLMap map = new XMLMap();
        MapConverter mapConverter = (MapConverter) getStore().getXMLConverter();
        mapConverter.toMap(x, map);
        return map;
    }

    /**
     * Take the <b>updated</b> values for the object and return a new, updated object.
     * This does not store it, so you have to do that if you want to keep the changes.
     *
     * @param map
     */
    protected Identifiable fromXMLMap(XMLMap map) {

        Identifiable identifiable = getStore().create();
        MapConverter mapConverter = (MapConverter) getStore().getXMLConverter();
        mapConverter.fromMap(map, identifiable);
        return identifiable;
    }

    /**
     * Add to an existing entry.
     *
     * @param identifiable
     * @param jjj
     */
    protected void addEntry(Identifiable identifiable, JSON jjj) {
        if (!(jjj instanceof JSONObject)) {
            say("sorry, but that is not a valid JSON object for this operation.");
            return;
        }
        JSONObject json = (JSONObject) jjj;
        XMLMap object = toXMLMap(identifiable);
        for (Object k : json.keySet()) {
            String key = k.toString();
            Object newValue = json.get(key);
            if (hasKey(key)) {
                Object currentValue = object.containsValue(k);
                if (currentValue == null) {
                    object.put(key, newValue);
                } else {
                    if (currentValue instanceof JSONArray) {
                        ((JSONArray) currentValue).add(newValue);
                    } else {
                        object.put(key, newValue);
                    }
                }
            } else {
                say("sorry, but \"" + key + "\" is not a recognized key. Skipping...");
            }
        }
        getStore().save(fromXMLMap(object));
    }

    protected void addEntry(Identifiable identifiable, String key, String value) {
        XMLMap object = toXMLMap(identifiable);
        if (hasKey(key)) {
            Object currentValue = object.get(key);
            if (currentValue == null) {
                object.put(key, value);
            } else {
                if (currentValue instanceof JSONArray) {
                    ((JSONArray) currentValue).add(value);
                } else {
                    object.put(key, value);
                }
            }
        } else {
            say("sorry, but \"" + key + "\" is not a recognized key. Skipping...");

        }
        getStore().save(fromXMLMap(object));
    }


    protected void removeEntries(Identifiable identifiable, List<String> keys) {
        XMLMap object = toXMLMap(identifiable);
        boolean gotOne = false;
        for (String key : keys) {
            if (hasKey(key)) {
                if (object.containsKey(key)) {
                    object.remove(key);
                    gotOne = true;
                }
            }
        }
        if (gotOne) {
            getStore().save(fromXMLMap(object));
        }

    }

    protected void removeEntry(Identifiable identifiable, String key) {
        XMLMap object = toXMLMap(identifiable);
        if (hasKey(key)) {
            if (object.containsKey(key)) {
                object.remove(key);
                getStore().save(fromXMLMap(object));
            } else {
                say("key \"" + key + "\" not found for this object.");
            }
        }
    }


    protected void showEntries(Identifiable identifiable, List<String> keys, boolean isVerbose) {
        XMLMap object = toXMLMap(identifiable);
        int leftWidth = 0;
        TreeMap<String, String> tMap = new TreeMap<>();
        for (String x : keys) {
            leftWidth = Math.max(leftWidth, x.length());
            tMap.put(x, object.getString(x));
        }

        for (String key : tMap.keySet()) {
            String v = tMap.get(key);
            // Suppress null entries. Record empty ones.
            if (!StringUtils.isTrivial(v)) {
                say(formatLongLine(key, v, leftWidth, isVerbose));
            }
        }
    }

    protected void showEntry(Identifiable identifiable, String key, boolean isVerbose) {
        if (hasKey(key)) {
            XMLMap object = toXMLMap(identifiable);
            if (object.containsKey(key)) {
                Object v = object.get(key);
                try {
                    JSON json = JSONSerializer.toJSON(v);
                    say(key + ":\n" + json.toString(1));

                } catch (Throwable t) {
                    say(key + " :\n" + object.get(key));
                }
            } else {
                say("(no value)");
            }
        } else {
            say("sorry, but \"" + key + "\" is not a recognized key. Skipping...");

        }

    }

    protected boolean hasKey(String key) {
        XMLConverter xmlConverter = getStore().getXMLConverter();
        if (xmlConverter instanceof MapConverter) {
            MapConverter mc = (MapConverter) xmlConverter;
            return mc.getKeys().allKeys().contains(key);
        }
        return false;
    }

    public void list_keys(InputLine inputLine) throws Exception {
        if (showHelp(inputLine)) {
            showListKeysHelp();
            return;
        }
        XMLConverter xmlConverter = getStore().getXMLConverter();
        if (xmlConverter instanceof MapConverter) {
            MapConverter mc = (MapConverter) xmlConverter;
            TreeSet<String> kk = new TreeSet<>();
            kk.addAll(mc.getKeys().allKeys());
            // print them in order.
            for (String key : kk) {
                say(key);
            }
        }
    }

    protected void showListKeysHelp() {
        say("list_keys");
        sayi("This lists the keys of the current store.");
    }

    @Override
    public void edit(InputLine inputLine) {
        Identifiable x = findItem(inputLine);
        XMLMap c = new XMLMap();
        getStore().getXMLConverter().toMap(x, c);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            c.toXML(baos);
            baos.close();
            String raw = new String(baos.toString("UTF-8"));
            LineEditor lineEditor = new LineEditor(raw);
            lineEditor.execute();
            String rc = getInput("save (y/n)?", "y");
            if (rc.equals("y")) {
                byte[] bytes = lineEditor.bufferToString().getBytes("UTF-8");
                XMLMap map = new XMLMap();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                map.fromXML(bais);
                bais.close();
                Identifiable identifiable = getStore().getXMLConverter().fromMap(map, null);
                getStore().save(identifiable);
            } else {
                say("changes ignored.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }


    String LIST_START_DELIMITER = "[";
    String LIST_END_DELIMITER = "]";
    String LIST_SEPARATOR = ",";

    protected List<String> getKeysList(InputLine inputLine) {
        List<String> list = new ArrayList<>();
        String rawLine = inputLine.getOriginalLine();
        if (rawLine == null || rawLine.isEmpty()) {
            return list;
        }
        int startListIndex = rawLine.indexOf(LIST_START_DELIMITER);
        int endListIndex = rawLine.indexOf(LIST_END_DELIMITER);
        if (startListIndex == -1 || endListIndex == -1) {
            return list;
        }
        String rawList = rawLine.substring(startListIndex + 1, endListIndex);
        StringTokenizer st = new StringTokenizer(rawList, LIST_SEPARATOR);
        while (st.hasMoreElements()) {
            list.add(st.nextToken().trim());
        }

        return list;
    }

    public static void main(String[] args) {
        CommandLineTokenizer CLT = new CommandLineTokenizer();
        String raw = "update -add -json '{\"fnord\":[\"blarf0\",\"blarf1\"]}' /foo:bar";

        Vector v = CLT.tokenize(raw);
        System.out.println(v);
        InputLine inputLine = new InputLine(v);
    }


}
