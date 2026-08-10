#ifndef SIMPLE_JSON_H
#define SIMPLE_JSON_H

#include <map>
#include <string>
#include <vector>

class JsonValue {
public:
    enum class Type {
        Null,
        Bool,
        Number,
        String,
        Array,
        Object
    };

    JsonValue();
    JsonValue(bool value);
    JsonValue(int value);
    JsonValue(double value);
    JsonValue(const char* value);
    JsonValue(const std::string& value);

    static JsonValue array();
    static JsonValue object();

    bool isNull() const;
    bool isBool() const;
    bool isNumber() const;
    bool isString() const;
    bool isArray() const;
    bool isObject() const;
    bool has(const std::string& key) const;

    int asInt(int fallback = 0) const;
    double asDouble(double fallback = 0.0) const;
    bool asBool(bool fallback = false) const;
    std::string asString(const std::string& fallback = "") const;

    JsonValue& operator[](const std::string& key);
    const JsonValue& operator[](const std::string& key) const;
    const JsonValue& operator[](size_t index) const;
    void append(const JsonValue& value);
    size_t size() const;

    const std::vector<JsonValue>& arrayItems() const;
    const std::map<std::string, JsonValue>& objectItems() const;

    std::string stringify() const;

private:
    Type type_;
    bool boolValue_;
    double numberValue_;
    std::string stringValue_;
    std::vector<JsonValue> arrayValue_;
    std::map<std::string, JsonValue> objectValue_;
};

class SimpleJson {
public:
    static bool parse(const std::string& text, JsonValue& value, std::string& error);
};

#endif // SIMPLE_JSON_H
