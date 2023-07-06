package org.example;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class YourConvertor {

    public static void main(String[] args) throws Exception {
        YourConvertor yourConvertor = new YourConvertor();
    }

    public String serialize(Object object) {
        HashMap<String,String> fieldsMap = new HashMap<>();

        Class<?> clazz = object.getClass();

        if (!clazz.getSuperclass().getName().equals("java.lang.Object")) {
            Field[] fields = clazz.getSuperclass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = "\"" + field.getName() + "\"";
                Object fieldValue = null;
                try {
                    fieldValue = field.get(object);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                String fieldValueString;
                if (fieldValue instanceof ArrayList){
                    fieldValueString = getStringFromArrayList((ArrayList<?>) fieldValue);
                } else if (fieldValue instanceof String || fieldValue instanceof Character){
                    fieldValueString = "\"" + fieldValue + "\"";
                } else if (fieldValue instanceof Integer || fieldValue instanceof Double || fieldValue instanceof Float) {
                    fieldValueString = fieldValue.toString();
                }
                else {
                    fieldValueString = serialize(fieldValue);
                }
                fieldsMap.put(fieldName,fieldValueString);
            }
        }

        Field[] fields = clazz.getDeclaredFields();
        Method[] methods = clazz.getDeclaredMethods();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = "\"" + field.getName() + "\"";
            Object fieldValue = null;
            try {
                fieldValue = field.get(object);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            String fieldValueString = null;

            boolean flag = false;
            String getterName = "get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
            for (Method method:methods){
                if (method.getName().equals(getterName)) {
                    try {
                        if (!method.invoke(object).equals(fieldValue)){
                            fieldValueString ="\"" + method.invoke(object) + "\"";
                            flag = true;
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if (flag) {
                fieldsMap.put(fieldName,fieldValueString);
                continue;
            }

            if (fieldValue instanceof ArrayList){
                fieldValueString = getStringFromArrayList((ArrayList<?>) fieldValue);
            } else if (fieldValue instanceof String || fieldValue instanceof Character){
                fieldValueString = "\"" + fieldValue + "\"";
            } else if (fieldValue instanceof Integer || fieldValue instanceof Double || fieldValue instanceof Float) {
                fieldValueString = fieldValue.toString();
            }
            else {
                fieldValueString = serialize(fieldValue);
            }
            fieldsMap.put(fieldName,fieldValueString);
        }

        return getJsonString(fieldsMap);
    }

    public <T> T deserialize(String jsonString, String className) {
        Map<String, String> jsonMap = parseJsonToMap(jsonString);
        Class<T> clazz = getClassByName(className);
        return createObjectFromJsonMap(jsonMap, clazz);
    }

    private static String getStringFromArrayList(ArrayList<?> arrayList){
        StringBuilder output = new StringBuilder("[");
        int counter = arrayList.size();
        boolean isString = arrayList.get(0) instanceof String;
        for (Object item: arrayList){
            if (isString) output.append("\"").append(item.toString()).append("\"");
            else output.append(item.toString());
            if (counter == 1) output.append("]");
            else output.append(",");
            counter--;
        }
        return output.toString();
    }

    private static String getJsonString(HashMap<String,String> fieldsMap){
        List<String> sortedKeys = new ArrayList<>(fieldsMap.keySet());
        Collections.sort(sortedKeys);

        StringBuilder output = new StringBuilder("{");
        int counter = 0;
        for (String key:sortedKeys){
            String value = fieldsMap.get(key);
            output.append(key).append(":").append(value);
            if (counter == sortedKeys.size() - 1) output.append("}");
            else output.append(",");
            counter++;
        }
        return output.toString();
    }

    private static String[] splitString(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int bracketCount = 0;
        for (char c : input.toCharArray()) {
            if (c == '{' || c == '[') {
                bracketCount++;
            } else if (c == '}' || c == ']') {
                bracketCount--;
            }
            if (c == ',' && bracketCount == 0) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
    }

    private static Map<String, String> parseJsonToMap(String jsonString) {
        String[] fields = splitString(removeBraces(jsonString));
        Map<String, String> map = new HashMap<>();
        for (String pair: fields){
            String[] parts = pair.split(":", 2);
            String key = removeQuotes(parts[0].trim()).trim();
            String value = removeQuotes(parts[1]);
            map.put(key, value);
        }
        return map;
    }

    private <T> T createObjectFromJsonMap(Map<String, String> jsonMap, Class<T> clazz) {
        try {
            T object = clazz.newInstance();
            if (!clazz.getSuperclass().getName().equals("java.lang.Object")) {
                Field[] fields = clazz.getSuperclass().getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    String fieldValue = jsonMap.get(fieldName);
                    if (fieldValue != null) {
                        Class<?> fieldType = field.getType();
                        if (fieldType.equals(int.class) || fieldType.equals(Integer.class)) {
                            field.setInt(object, Integer.parseInt(fieldValue));
                        } else if (fieldType.equals(double.class) || fieldType.equals(Double.class)) {
                            field.setDouble(object, Double.parseDouble(fieldValue));
                        } else if (fieldType.equals(float.class) || fieldType.equals(Float.class)) {
                            field.setFloat(object, Float.parseFloat(fieldValue));
                        } else if (fieldType.equals(char.class) || fieldType.equals(Character.class)) {
                            field.setChar(object, fieldValue.charAt(0));
                        } else if (fieldType.equals(String.class)) {
                            field.set(object, fieldValue);
                        } else if (fieldType.equals(ArrayList.class)) {
                            String[] elements = removeBrackets(fieldValue).split(",");
                            ArrayList<Integer> list = new ArrayList<>();
                            for (String element : elements) {
                                list.add(Integer.parseInt(element));
                            }
                            field.set(object, list);
                        } else {
                            field.set(object, deserialize(fieldValue,field.getType().getName()));
                        }
                    }
                }
            }
            Field[] fields = clazz.getDeclaredFields();
            Method[] methods = clazz.getDeclaredMethods();
            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                String fieldValue = jsonMap.get(fieldName);

                boolean flag = false;
                String setterName = "set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
                for (Method method:methods){
                    if (method.getName().equals(setterName)) {
                        method.invoke(object, fieldValue);
                        flag = true;
                    }
                }

                if (flag) continue;

                if (fieldValue != null) {
                    Class<?> fieldType = field.getType();
                    if (fieldType.equals(int.class) || fieldType.equals(Integer.class)) {
                        field.setInt(object, Integer.parseInt(fieldValue));
                    } else if (fieldType.equals(double.class) || fieldType.equals(Double.class)) {
                        field.setDouble(object, Double.parseDouble(fieldValue));
                    } else if (fieldType.equals(float.class) || fieldType.equals(Float.class)) {
                        field.setFloat(object, Float.parseFloat(fieldValue));
                    } else if (fieldType.equals(char.class) || fieldType.equals(Character.class)) {
                        field.set(object, fieldValue);
                    } else if (fieldType.equals(String.class)) {
                        field.set(object, fieldValue);
                    } else if (fieldType.equals(ArrayList.class)) {
                        String[] elements = removeBrackets(fieldValue).split(",");
                        ArrayList<Integer> list = new ArrayList<>();
                        for (String element : elements) {
                            list.add(Integer.parseInt(element));
                        }
                        field.set(object, list);
                    } else {
                        field.set(object, deserialize(fieldValue,field.getType().getName()));
                    }
                }
            }
            return object;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static <T> Class<T> getClassByName(String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String removeQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    public static String removeBrackets(String input) {
        if (input.length() >= 2 && input.startsWith("[") && input.endsWith("]")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    public static String removeBraces(String input) {
        if (input.length() >= 2 && input.startsWith("{") && input.endsWith("}")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }
}