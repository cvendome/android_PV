package com.androidpv.java.xposed;

import java.io.*;
import java.util.*;

/**
 * Created by Erin on 2/27/16.
 *
 * ModuleBuilder takes in the parsed code generated by Parser and constructs an Xposed module.
 */
public class ModuleBuilder {

    private File sourceFile;

    private boolean DO_NOT_PRINT = false;

    /**
     * Constructor for ModuleBuilder. Creates a text file containing the Xposed module source code.
     *
     * @param fileName  String name of the file containing the parsed code outputted by Parser
     */
    public ModuleBuilder(String fileName) {

        System.out.println("in module builder");

        this.sourceFile = new File(fileName);
        boolean beginningOfFile = true;

        try {
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("moduleFile.java")));

            List<Map<String, ArrayList<String>>> packagesAndAnonClasses = getPackagesAndAnonClasses(this.sourceFile);
            List<String> packageNamesList = packagesAndAnonClasses.get(0).get("0");
            Map<String, ArrayList<String>> anonClassMap = packagesAndAnonClasses.get(1);

            writer.println(MBConstants.MODULE_PACKAGE_NAME);
            writer.println(MBConstants.IMPORTS);
            writer.println(MBConstants.CLASS_NAME_MAIN_METHOD);
            writer.println(addMainIfClausePackages(packageNamesList));
            writer.println(MBConstants.PREFERENCES);

            // Header of code done. Now need to write hooks for each method

            BufferedReader reader = new BufferedReader(new FileReader(this.sourceFile));
            String line;

            String packageName = ""; // need to check that package name is different

            while ((line = reader.readLine()) != null) {
                String[] splitString = line.split(MBConstants.PARSED_FILE_SEPARATOR);
                for (int i = 0; i < splitString.length; i++) {
                    splitString[i] = splitString[i].trim();
                }

                if (!splitString[MBConstants.PACKAGE_INDEX].equals(packageName)) {
                    if (!beginningOfFile) {
                        writer.println(MBConstants.END_OF_IF_CLAUSE);
                    }
                    beginningOfFile = false;
                    writer.println(addPackageNameCheck(splitString[MBConstants.PACKAGE_INDEX]));
                    packageName = splitString[MBConstants.PACKAGE_INDEX];
                    System.out.println(packageName);
                }

                List<int[]> anonClassNums = anonClassCheck(splitString, anonClassMap);
                String findHook = "";
                if (anonClassNums != null) {
               // if ((anonClassNum != 0) && (anonClassNum != 1)){
                    for (int tryIter = 0; tryIter < anonClassNums.size(); tryIter++) {
                        writer.println(MBConstants.TRY_BLOCK_BEGINNING);
                        findHook = addFindHook(splitString, anonClassMap, anonClassNums.get(tryIter));
                        if (!DO_NOT_PRINT) {
                            writer = addHookMethodBlock(writer, findHook, splitString);
                        }
                        writer.println(MBConstants.TRY_BLOCK_END_FULL);
                    }
                }
                else {
                    findHook = addFindHook(splitString, anonClassMap, new int[0]);
                    if (!DO_NOT_PRINT) {
                        writer = addHookMethodBlock(writer, findHook, splitString);
                    }
                }

                DO_NOT_PRINT = false;

            }
            writer.println(MBConstants.END_OF_CODE);
            reader.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.sourceFile.deleteOnExit();
        System.out.println("Module built.");
    }  // End of Constructor


    /**
     * This method takes in each bit of information from each line of the parsed code outputted by Parser and
     * constructs the findAndHookMethod/Constructor clause.
     *
     * @param methodInfo  each line of the parsed file outputted by Parser split into a String array
     * @return  the findAndHookMethod/Constructor as String
     */
    private String addFindHook(String[] methodInfo, Map<String, ArrayList<String>> anonMap, int[] anonNums) {

        DO_NOT_PRINT = false;

        StringBuilder hookMethodBuilder = new StringBuilder();

        String packageName = methodInfo[MBConstants.PACKAGE_INDEX];
        String className = methodInfo[MBConstants.CLASS_INDEX];
        List<String> parentList = convertStringToList(methodInfo[MBConstants.PARENT_INDEX]);
        List<String> anonClassList = convertStringToList(methodInfo[MBConstants.ANON_CLASS_INDEX]);
        List<String> parentModifiersList = convertStringToList(methodInfo[MBConstants.PARENT_MODIFIERS_INDEX]);
        String methodName = methodInfo[MBConstants.METHOD_INDEX];
        String parameters = methodInfo[MBConstants.PARAMETERS_INDEX];
        String modifiers = methodInfo[MBConstants.MODIFIERS_INDEX];
        boolean isConstructor = Boolean.parseBoolean(methodInfo[MBConstants.CONSTRUCTOR_BOOL_INDEX]);
        boolean isInterface = Boolean.parseBoolean(methodInfo[MBConstants.INTERFACE_BOOL_INDEX]);
        boolean nestedClassBoolean = false;

        if (modifiers.contains("abstract")) {
            DO_NOT_PRINT = true;
        }
        if (isInterface) {
            DO_NOT_PRINT = true;
        }
        if (!modifiers.contains("private")) {
            if (!modifiers.contains("public")) {
                if (!modifiers.contains("protected")) {
                    DO_NOT_PRINT = true;
                }
            }
        }

        String findHookInit;
        String classParentChain = getParentString(parentList, anonMap, anonNums);

        if (classParentChain.contains("$")) {
            nestedClassBoolean = true;
        }

        if (isConstructor) {
            if (nestedClassBoolean) {
                if (parentModifiersList.contains("static")) {
                    methodName = "";
                }
                else {
                    // replaces methodName with call to super instance
                    int lastDollarSign = classParentChain.lastIndexOf("$");
                    String classChain = classParentChain.substring(0, lastDollarSign);
                    methodName = packageName + "." + classChain;
                }
            }
            else {
                methodName = "";
            }
            findHookInit = MBConstants.FIND_HOOK_CONSTRUCTOR_STRING;
        }
        else {
            findHookInit = MBConstants.FIND_HOOK_METHOD_STRING;
        }

        // paramString results in all formatted parameters for that method
        String paramString = getParametersString(parameters);

        String findHookMethodPt1 = findHookInit + packageName + "." + classParentChain
                + MBConstants.LPPARAM_CLASS_LOADER_STRING;

        if (methodName.equals("")) {
            findHookMethodPt1 += paramString;
        }
        else {
            findHookMethodPt1 += MBConstants.COMMA_QUOTE + methodName + "\"" + paramString;
        }

        hookMethodBuilder.append(findHookMethodPt1);
        hookMethodBuilder.append(MBConstants.END_OF_FIND_HOOK_METHOD);

        return hookMethodBuilder.toString();
    }


    private String getParentString(List<String> parentsList, Map<String, ArrayList<String>> anonMap, int[] anonNum) {
        String parentString = "";
        String className = parentsList.get(parentsList.size()-1).trim();
        int anonNumIter = 0;
        for (int i = parentsList.size()-1; i >= 0; i--) {
            String parent = parentsList.get(i).trim();
            if (anonMap.containsKey(className)) {
                if (anonMap.get(className).contains(parent)) {
                    // parent is anonymous class
                    if (anonNum.length == 0) {
                        System.err.println("className: " + className + " parent: " + parent);
                    }
                    parent = String.valueOf(anonNum[anonNumIter]);
                    anonNumIter++;
                }
            }
            parentString += parent;
            if (i != 0) {
                parentString += "$";
            }
        }
        return parentString;
    }


    private String getParametersString(String parameters) {
        String paramString = "";
        if (!parameters.equals("[]")) {

            // remove brackets
            parameters = parameters.substring(1, parameters.length() - 1);

            // convert parameters into list, splitting on ,
            String[] parameterArray = parameters.split(",");

            for (String parameter : parameterArray) {
                paramString += ", \"" + parameter.trim() + "\"";
            }
        }
        return paramString;
    }


    private List<String> convertStringToList(String listString) {
        List<String> list = new ArrayList<>();
        if (!listString.equals("[]")) {
            listString = listString.replace("[", "");
            listString = listString.replace("]", "");

            list = Arrays.asList(listString.split(","));
        }
        int i = 0;
        for (String item : list) {
            item = item.trim();
            list.set(i, item);
            i++;
        }

        return list;
    }


    private PrintWriter addHookMethodBlock(PrintWriter writer, String findHook, String[] splitString) {

        writer.println(findHook);
        writer.println(addHook(splitString[MBConstants.METHOD_INDEX], MBConstants.BEFORE_STRING,
                MBConstants.START_STRING, MBConstants.METHOD_START_TIME));
        writer.println(addHook(splitString[MBConstants.METHOD_INDEX], MBConstants.AFTER_STRING, MBConstants.END_STRING,
                MBConstants.METHOD_END_TIME));
        return writer;
    }



    /**
     * This method returns the proper subsection of findAndHookMethod, either "beforeHookMethod" or "afterHookMethod".
     *
     * @param method  the method we are analyzing
     * @param timeInstance  specifies whether we are running "beforeHookMethod" or "afterHookMethod"
     * @param methodTime  specifies whether we are capturing methodStart or methodEnd - matches timeInstance
     * @return  the "beforeHookMethod" or "afterHookMethod"
     */
    private String addHook(String method, String timeInstance, String startEndTime, String methodTime) {
        String hook = MBConstants.ADD_HOOK_METHOD_BEGINNING + timeInstance
                + MBConstants.ADD_HOOK_METHOD_END_PART1 + startEndTime + MBConstants.ADD_HOOK_METHOD_END_PART2 + method
                + methodTime;
        return hook;
    }


    /**
     * This method returns the IF clause for a given package specified in the module. If we pass this IF clause, we
     * start finding and hooking the methods in that package.
     *
     * @param packageName  the package name of the specific IF clause we are writing
     * @return  the IF clause checking if we are in the correct package to execute the correct methods
     */
    private String addPackageNameCheck(String packageName) {
        String ifClause = MBConstants.PACKAGE_NAME_IF_BEGINNING + packageName + MBConstants.PACKAGE_NAME_IF_END;
        return ifClause;
    }


    /** NEEDS EDIT
     * This method generates the String checking that we are working in the correct package. It is not for each
     * individual package but rather prevents the module from proceeding if we are not in a package the module
     * recognizes.
     *
     * @param
     * @return  the main IF clause containing the package names. Returned as a String
     */
    private String addMainIfClausePackages(List<String> packageNamesList) {
        StringBuilder ifClause = new StringBuilder();
        ifClause.append(MBConstants.MAIN_PACKAGE_IF_CLAUSE_BEGINNING + MBConstants.MAIN_LPPARAM_PACKAGENAME_EQUALS);

        int i = 0;
        while (i < packageNamesList.size()) {
            ifClause.append(packageNamesList.get(i));
            i++;
            if (i != packageNamesList.size()) {
                ifClause.append(MBConstants.MAIN_PACKAGE_IF_CLAUSE_OR + MBConstants.MAIN_LPPARAM_PACKAGENAME_EQUALS);
            }
        }
        ifClause.append(MBConstants.MAIN_PACKAGE_IF_CLAUSE_END);

        return ifClause.toString();
    }


    /**  NEEDS EDIT
     * This method does an initial runthrough of the parsed source code outputted by Parser to gather all of the
     * package names. The package names are required for the main IF clause of the module.
     *
     * @param file  the file containing the output of Parser
     * @return  a list of all the packages in the source code
     */
    private List<Map<String, ArrayList<String>>> getPackagesAndAnonClasses(File file) {
        ArrayList<String> packageNamesList = new ArrayList<>();
        Map<String, ArrayList<String>> anonMap = new HashMap<>(); // [parent: [anonClass1, anonClass2, ...]]

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            String packageName = ""; // need to check that package name is different

            while ((line = reader.readLine()) != null) {
                String[] splitString = line.split(MBConstants.PARSED_FILE_SEPARATOR);
                String className = splitString[MBConstants.CLASS_INDEX];
                ArrayList<String> anonClassList = new ArrayList<>(convertStringToList(splitString[MBConstants.ANON_CLASS_INDEX]));
                if (!splitString[MBConstants.PACKAGE_INDEX].equals(packageName)) {
                    packageName = splitString[MBConstants.PACKAGE_INDEX];
                    packageNamesList.add(packageName);
                }
                if (!anonClassList.isEmpty()) {
                    anonMap = addAnonClass(className, anonClassList, anonMap);
                }
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Map<String, ArrayList<String>>> packagesAndAnonClasses = new ArrayList<>();
        Map<String, ArrayList<String>> packageMap = new HashMap<>();
        packageMap.put("0", packageNamesList);
        packagesAndAnonClasses.add(packageMap);
        packagesAndAnonClasses.add(anonMap);

        return packagesAndAnonClasses;
    }


    /** NEEDS EDIT
     * Helper method
     *
     * @param className
     * @param anonClassList
     * @param anonClassMap
     * @return
     */
    private Map<String, ArrayList<String>> addAnonClass(String className, ArrayList<String> anonClassList, Map<String, ArrayList<String>> anonClassMap) {

        if (anonClassMap.containsKey(className)) {
            for (String anon : anonClassList) {
                if (!anonClassMap.get(className).contains(anon)) {
                    anonClassMap.get(className).add(anon);
                }
            }
        }
        else {
            anonClassMap.put(className, anonClassList);
        }

        return anonClassMap;
    }


    /**
     * Helper method
     *
     * @param methodInfo
     * @param anonClassMap
     * @return
     */
    private List<int[]> anonClassCheck(String[] methodInfo, Map<String, ArrayList<String>> anonClassMap) {

        String className = methodInfo[MBConstants.CLASS_INDEX];
        List<String> anonClasses = convertStringToList(methodInfo[MBConstants.ANON_CLASS_INDEX]);

        if (anonClasses.isEmpty()) {
            return null;
        }
        else {  // we have anonymous classes
            Permutation permutation = new Permutation(anonClassMap.get(className).size(), anonClasses.size());
            return permutation.getAnonOptions();
        }
    }
}
