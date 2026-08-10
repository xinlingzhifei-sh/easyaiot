#include "SimpleJson.h"

#include <cctype>
#include <cmath>
#include <cstdlib>
#include <iomanip>
#include <sstream>

namespace {

const JsonValue& nullValue() {
    static const JsonValue value;
    return value;
}

std::string escapeString(const std::string& value) {
    std::ostringstream out;
    out << '"';
    for (char ch : value) {
        switch (ch) {
            case '"':
                out << "\\\"";
                break;
            case '\\':
                out << "\\\\";
                break;
            case '\b':
                out << "\\b";
                break;
            case '\f':
                out << "\\f";
                break;
            case '\n':
                out << "\\n";
                break;
            case '\r':
                out << "\\r";
                break;
            case '\t':
                out << "\\t";
                break;
            default:
                if (static_cast<unsigned char>(ch) < 0x20) {
                    out << "\\u" << std::hex << std::setw(4) << std::setfill('0')
                        << static_cast<int>(static_cast<unsigned char>(ch));
                } else {
                    out << ch;
                }
                break;
        }
    }
    out << '"';
    return out.str();
}

class Parser {
public:
    explicit Parser(const std::string& text) : text_(text) {}

    bool parse(JsonValue& value, std::string& error) {
        skipWhitespace();
        if (!parseValue(value, error)) {
            return false;
        }
        skipWhitespace();
        if (pos_ != text_.size()) {
            error = "Unexpected trailing content";
            return false;
        }
        return true;
    }

private:
    const std::string& text_;
    size_t pos_{0};

    void skipWhitespace() {
        while (pos_ < text_.size() && std::isspace(static_cast<unsigned char>(text_[pos_]))) {
            ++pos_;
        }
    }

    bool consume(char expected) {
        if (pos_ < text_.size() && text_[pos_] == expected) {
            ++pos_;
            return true;
        }
        return false;
    }

    bool consumeLiteral(const char* literal) {
        size_t start = pos_;
        for (const char* p = literal; *p; ++p) {
            if (pos_ >= text_.size() || text_[pos_] != *p) {
                pos_ = start;
                return false;
            }
            ++pos_;
        }
        return true;
    }

    bool parseValue(JsonValue& value, std::string& error) {
        if (pos_ >= text_.size()) {
            error = "Unexpected end of JSON";
            return false;
        }

        char ch = text_[pos_];
        if (ch == '{') {
            return parseObject(value, error);
        }
        if (ch == '[') {
            return parseArray(value, error);
        }
        if (ch == '"') {
            std::string parsed;
            if (!parseString(parsed, error)) {
                return false;
            }
            value = JsonValue(parsed);
            return true;
        }
        if (ch == '-' || std::isdigit(static_cast<unsigned char>(ch))) {
            return parseNumber(value, error);
        }
        if (consumeLiteral("true")) {
            value = JsonValue(true);
            return true;
        }
        if (consumeLiteral("false")) {
            value = JsonValue(false);
            return true;
        }
        if (consumeLiteral("null")) {
            value = JsonValue();
            return true;
        }

        error = "Unexpected JSON token";
        return false;
    }

    bool parseObject(JsonValue& value, std::string& error) {
        if (!consume('{')) {
            error = "Expected object";
            return false;
        }

        value = JsonValue::object();
        skipWhitespace();
        if (consume('}')) {
            return true;
        }

        while (true) {
            skipWhitespace();
            std::string key;
            if (!parseString(key, error)) {
                return false;
            }
            skipWhitespace();
            if (!consume(':')) {
                error = "Expected ':' after object key";
                return false;
            }
            skipWhitespace();
            JsonValue item;
            if (!parseValue(item, error)) {
                return false;
            }
            value[key] = item;
            skipWhitespace();
            if (consume('}')) {
                return true;
            }
            if (!consume(',')) {
                error = "Expected ',' or '}' in object";
                return false;
            }
        }
    }

    bool parseArray(JsonValue& value, std::string& error) {
        if (!consume('[')) {
            error = "Expected array";
            return false;
        }

        value = JsonValue::array();
        skipWhitespace();
        if (consume(']')) {
            return true;
        }

        while (true) {
            skipWhitespace();
            JsonValue item;
            if (!parseValue(item, error)) {
                return false;
            }
            value.append(item);
            skipWhitespace();
            if (consume(']')) {
                return true;
            }
            if (!consume(',')) {
                error = "Expected ',' or ']' in array";
                return false;
            }
        }
    }

    bool parseString(std::string& value, std::string& error) {
        if (!consume('"')) {
            error = "Expected string";
            return false;
        }

        value.clear();
        while (pos_ < text_.size()) {
            char ch = text_[pos_++];
            if (ch == '"') {
                return true;
            }
            if (ch != '\\') {
                value += ch;
                continue;
            }
            if (pos_ >= text_.size()) {
                error = "Unterminated escape sequence";
                return false;
            }
            char escaped = text_[pos_++];
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    value += escaped;
                    break;
                case 'b':
                    value += '\b';
                    break;
                case 'f':
                    value += '\f';
                    break;
                case 'n':
                    value += '\n';
                    break;
                case 'r':
                    value += '\r';
                    break;
                case 't':
                    value += '\t';
                    break;
                case 'u':
                    if (pos_ + 4 > text_.size()) {
                        error = "Invalid unicode escape";
                        return false;
                    }
                    value += '?';
                    pos_ += 4;
                    break;
                default:
                    error = "Invalid escape sequence";
                    return false;
            }
        }

        error = "Unterminated string";
        return false;
    }

    bool parseNumber(JsonValue& value, std::string& error) {
        const char* start = text_.c_str() + pos_;
        char* end = nullptr;
        double parsed = std::strtod(start, &end);
        if (end == start) {
            error = "Invalid number";
            return false;
        }
        pos_ += static_cast<size_t>(end - start);
        value = JsonValue(parsed);
        return true;
    }
};

} // namespace

JsonValue::JsonValue() : type_(Type::Null), boolValue_(false), numberValue_(0.0) {}

JsonValue::JsonValue(bool value) : type_(Type::Bool), boolValue_(value), numberValue_(0.0) {}

JsonValue::JsonValue(int value) : type_(Type::Number), boolValue_(false), numberValue_(value) {}

JsonValue::JsonValue(double value) : type_(Type::Number), boolValue_(false), numberValue_(value) {}

JsonValue::JsonValue(const char* value) : type_(Type::String), boolValue_(false), numberValue_(0.0), stringValue_(value ? value : "") {}

JsonValue::JsonValue(const std::string& value) : type_(Type::String), boolValue_(false), numberValue_(0.0), stringValue_(value) {}

JsonValue JsonValue::array() {
    JsonValue value;
    value.type_ = Type::Array;
    return value;
}

JsonValue JsonValue::object() {
    JsonValue value;
    value.type_ = Type::Object;
    return value;
}

bool JsonValue::isNull() const { return type_ == Type::Null; }
bool JsonValue::isBool() const { return type_ == Type::Bool; }
bool JsonValue::isNumber() const { return type_ == Type::Number; }
bool JsonValue::isString() const { return type_ == Type::String; }
bool JsonValue::isArray() const { return type_ == Type::Array; }
bool JsonValue::isObject() const { return type_ == Type::Object; }

bool JsonValue::has(const std::string& key) const {
    return isObject() && objectValue_.find(key) != objectValue_.end();
}

int JsonValue::asInt(int fallback) const {
    if (isNumber()) {
        return static_cast<int>(numberValue_);
    }
    if (isString()) {
        try {
            return std::stoi(stringValue_);
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

double JsonValue::asDouble(double fallback) const {
    if (isNumber()) {
        return numberValue_;
    }
    if (isString()) {
        try {
            return std::stod(stringValue_);
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

bool JsonValue::asBool(bool fallback) const {
    if (isBool()) {
        return boolValue_;
    }
    if (isNumber()) {
        return numberValue_ != 0.0;
    }
    if (isString()) {
        std::string text = stringValue_;
        for (char& ch : text) {
            ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
        }
        return text == "true" || text == "1" || text == "yes" || text == "on";
    }
    return fallback;
}

std::string JsonValue::asString(const std::string& fallback) const {
    if (isString()) {
        return stringValue_;
    }
    if (isNumber()) {
        std::ostringstream out;
        if (std::fabs(numberValue_ - std::round(numberValue_)) < 0.0000001) {
            out << static_cast<long long>(numberValue_);
        } else {
            out << numberValue_;
        }
        return out.str();
    }
    if (isBool()) {
        return boolValue_ ? "true" : "false";
    }
    return fallback;
}

JsonValue& JsonValue::operator[](const std::string& key) {
    if (!isObject()) {
        type_ = Type::Object;
        objectValue_.clear();
    }
    return objectValue_[key];
}

const JsonValue& JsonValue::operator[](const std::string& key) const {
    if (!isObject()) {
        return nullValue();
    }
    auto it = objectValue_.find(key);
    return it == objectValue_.end() ? nullValue() : it->second;
}

const JsonValue& JsonValue::operator[](size_t index) const {
    if (!isArray() || index >= arrayValue_.size()) {
        return nullValue();
    }
    return arrayValue_[index];
}

void JsonValue::append(const JsonValue& value) {
    if (!isArray()) {
        type_ = Type::Array;
        arrayValue_.clear();
    }
    arrayValue_.push_back(value);
}

size_t JsonValue::size() const {
    if (isArray()) {
        return arrayValue_.size();
    }
    if (isObject()) {
        return objectValue_.size();
    }
    return 0;
}

const std::vector<JsonValue>& JsonValue::arrayItems() const {
    static const std::vector<JsonValue> empty;
    return isArray() ? arrayValue_ : empty;
}

const std::map<std::string, JsonValue>& JsonValue::objectItems() const {
    static const std::map<std::string, JsonValue> empty;
    return isObject() ? objectValue_ : empty;
}

std::string JsonValue::stringify() const {
    switch (type_) {
        case Type::Null:
            return "null";
        case Type::Bool:
            return boolValue_ ? "true" : "false";
        case Type::Number: {
            std::ostringstream out;
            if (std::fabs(numberValue_ - std::round(numberValue_)) < 0.0000001) {
                out << static_cast<long long>(numberValue_);
            } else {
                out << numberValue_;
            }
            return out.str();
        }
        case Type::String:
            return escapeString(stringValue_);
        case Type::Array: {
            std::ostringstream out;
            out << "[";
            for (size_t i = 0; i < arrayValue_.size(); ++i) {
                if (i > 0) {
                    out << ",";
                }
                out << arrayValue_[i].stringify();
            }
            out << "]";
            return out.str();
        }
        case Type::Object: {
            std::ostringstream out;
            out << "{";
            bool first = true;
            for (const auto& pair : objectValue_) {
                if (!first) {
                    out << ",";
                }
                first = false;
                out << escapeString(pair.first) << ":" << pair.second.stringify();
            }
            out << "}";
            return out.str();
        }
    }
    return "null";
}

bool SimpleJson::parse(const std::string& text, JsonValue& value, std::string& error) {
    Parser parser(text);
    return parser.parse(value, error);
}
