// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package proptools

import (
	"fmt"
	"reflect"
	"sort"
	"strconv"
	"strings"
	"text/scanner"

	"github.com/google/blueprint/parser"
)

const maxUnpackErrors = 10

var (
	// Hard-coded list of allowlisted property names of type map. This is to limit use of maps to
	// where absolutely necessary.
	validMapProperties = []string{}
)

type UnpackError struct {
	Err error
	Pos scanner.Position
}

func (e *UnpackError) Error() string {
	return fmt.Sprintf("%s: %s", e.Pos, e.Err)
}

// packedProperty helps to track properties usage (`used` will be true)
type packedProperty struct {
	property *parser.Property
	used     bool
}

// unpackContext keeps compound names and their values in a map. It is initialized from
// parsed properties.
type unpackContext struct {
	propertyMap        map[string]*packedProperty
	validMapProperties map[string]bool
	errs               []error
}

// UnpackProperties populates the list of runtime values ("property structs") from the parsed properties.
// If a property a.b.c has a value, a field with the matching name in each runtime value is initialized
// from it. See PropertyNameForField for field and property name matching.
// For instance, if the input contains
//   { foo: "abc", bar: {x: 1},}
// and a runtime value being has been declared as
//   var v struct { Foo string; Bar int }
// then v.Foo will be set to "abc" and v.Bar will be set to 1
// (cf. unpack_test.go for further examples)
//
// The type of a receiving field has to match the property type, i.e., a bool/int/string field
// can be set from a property with bool/int/string value, a struct can be set from a map (only the
// matching fields are set), and an slice can be set from a list.
// If a field of a runtime value has been already set prior to the UnpackProperties, the new value
// is appended to it (see somewhat inappropriately named ExtendBasicType).
// The same property can initialize fields in multiple runtime values. It is an error if any property
// value was not used to initialize at least one field.
func UnpackProperties(properties []*parser.Property, objects ...interface{}) (map[string]*parser.Property, []error) {
	return unpackProperties(properties, validMapProperties, objects...)
}

func unpackProperties(properties []*parser.Property, validMapProps []string, objects ...interface{}) (map[string]*parser.Property, []error) {
	var unpackContext unpackContext
	unpackContext.propertyMap = make(map[string]*packedProperty)
	if !unpackContext.buildPropertyMap("", properties) {
		return nil, unpackContext.errs
	}
	unpackContext.validMapProperties = make(map[string]bool, len(validMapProps))
	for _, p := range validMapProps {
		unpackContext.validMapProperties[p] = true
	}

	for _, obj := range objects {
		valueObject := reflect.ValueOf(obj)
		if !isStructPtr(valueObject.Type()) {
			panic(fmt.Errorf("properties must be *struct, got %s",
				valueObject.Type()))
		}
		unpackContext.unpackToStruct("", valueObject.Elem())
		if len(unpackContext.errs) >= maxUnpackErrors {
			return nil, unpackContext.errs
		}
	}

	// Gather property map, and collect any unused properties.
	// Avoid reporting subproperties of unused properties.
	result := make(map[string]*parser.Property)
	var unusedNames []string
	for name, v := range unpackContext.propertyMap {
		if v.used {
			result[name] = v.property
		} else {
			unusedNames = append(unusedNames, name)
		}
	}
	if len(unusedNames) == 0 && len(unpackContext.errs) == 0 {
		return result, nil
	}
	return nil, unpackContext.reportUnusedNames(unusedNames)
}

func (ctx *unpackContext) reportUnusedNames(unusedNames []string) []error {
	sort.Strings(unusedNames)
	var lastReported string
	for _, name := range unusedNames {
		// if 'foo' has been reported, ignore 'foo\..*' and 'foo\[.*'
		if lastReported != "" {
			trimmed := strings.TrimPrefix(name, lastReported)
			if trimmed != name && (trimmed[0] == '.' || trimmed[0] == '[') {
				continue
			}
		}
		ctx.errs = append(ctx.errs, &UnpackError{
			fmt.Errorf("unrecognized property %q", name),
			ctx.propertyMap[name].property.ColonPos})
		lastReported = name
	}
	return ctx.errs
}

func (ctx *unpackContext) buildPropertyMap(prefix string, properties []*parser.Property) bool {
	nOldErrors := len(ctx.errs)
	for _, property := range properties {
		name := fieldPath(prefix, property.Name)
		if first, present := ctx.propertyMap[name]; present {
			ctx.addError(
				&UnpackError{fmt.Errorf("property %q already defined", name), property.ColonPos})
			if ctx.addError(
				&UnpackError{fmt.Errorf("<-- previous definition here"), first.property.ColonPos}) {
				return false
			}
			continue
		}

		ctx.propertyMap[name] = &packedProperty{property, false}
		switch propValue := property.Value.Eval().(type) {
		case *parser.Map:
			// If this is a map and the values are not primitive types, we need to unroll it for further
			// mapping. Keys are limited to string types.
			ctx.buildPropertyMap(name, propValue.Properties)
			if len(propValue.MapItems) == 0 {
				continue
			}
			items := propValue.MapItems
			keysType := items[0].Key.Type()
			valsAreBasic := primitiveType(items[0].Value.Type())
			if keysType != parser.StringType {
				ctx.addError(&UnpackError{Err: fmt.Errorf("complex key types are unsupported: %s", keysType)})
				return false
			} else if valsAreBasic {
				continue
			}
			itemProperties := make([]*parser.Property, len(items), len(items))
			for i, item := range items {
				itemProperties[i] = &parser.Property{
					Name:     fmt.Sprintf("%s{value:%d}", property.Name, i),
					NamePos:  property.NamePos,
					ColonPos: property.ColonPos,
					Value:    item.Value,
				}
			}
			if !ctx.buildPropertyMap(prefix, itemProperties) {
				return false
			}
		case *parser.List:
			// If it is a list, unroll it unless its elements are of primitive type
			// (no further mapping will be needed in that case, so we avoid cluttering
			// the map).
			if len(propValue.Values) == 0 {
				continue
			}
			if primitiveType(propValue.Values[0].Type()) {
				continue
			}

			itemProperties := make([]*parser.Property, len(propValue.Values), len(propValue.Values))
			for i, expr := range propValue.Values {
				itemProperties[i] = &parser.Property{
					Name:     property.Name + "[" + strconv.Itoa(i) + "]",
					NamePos:  property.NamePos,
					ColonPos: property.ColonPos,
					Value:    expr,
				}
			}
			if !ctx.buildPropertyMap(prefix, itemProperties) {
				return false
			}
		}
	}

	return len(ctx.errs) == nOldErrors
}

// primitiveType returns whether typ is a primitive type
func primitiveType(typ parser.Type) bool {
	return typ == parser.StringType || typ == parser.Int64Type || typ == parser.BoolType
}

func fieldPath(prefix, fieldName string) string {
	if prefix == "" {
		return fieldName
	}
	return prefix + "." + fieldName
}

func (ctx *unpackContext) addError(e error) bool {
	ctx.errs = append(ctx.errs, e)
	return len(ctx.errs) < maxUnpackErrors
}

func (ctx *unpackContext) unpackToStruct(namePrefix string, structValue reflect.Value) {
	structType := structValue.Type()

	for i := 0; i < structValue.NumField(); i++ {
		fieldValue := structValue.Field(i)
		field := structType.Field(i)

		// In Go 1.7, runtime-created structs are unexported, so it's not
		// possible to create an exported anonymous field with a generated
		// type. So workaround this by special-casing "BlueprintEmbed" to
		// behave like an anonymous field for structure unpacking.
		if field.Name == "BlueprintEmbed" {
			field.Name = ""
			field.Anonymous = true
		}

		if field.PkgPath != "" {
			// This is an unexported field, so just skip it.
			continue
		}

		propertyName := fieldPath(namePrefix, PropertyNameForField(field.Name))

		if !fieldValue.CanSet() {
			panic(fmt.Errorf("field %s is not settable", propertyName))
		}

		// Get the property value if it was specified.
		packedProperty, propertyIsSet := ctx.propertyMap[propertyName]

		origFieldValue := fieldValue

		// To make testing easier we validate the struct field's type regardless
		// of whether or not the property was specified in the parsed string.
		// TODO(ccross): we don't validate types inside nil struct pointers
		// Move type validation to a function that runs on each factory once
		switch kind := fieldValue.Kind(); kind {
		case reflect.Bool, reflect.String, reflect.Struct, reflect.Slice:
			// Do nothing
		case reflect.Map:
			// Restrict names of map properties that _can_ be set in bp files
			if _, ok := ctx.validMapProperties[propertyName]; !ok {
				if !HasTag(field, "blueprint", "mutated") {
					ctx.addError(&UnpackError{
						Err: fmt.Errorf("Uses of maps for properties must be allowlisted. %q is an unsupported use case", propertyName),
					})
				}
			}
		case reflect.Interface:
			if fieldValue.IsNil() {
				panic(fmt.Errorf("field %s contains a nil interface", propertyName))
			}
			fieldValue = fieldValue.Elem()
			elemType := fieldValue.Type()
			if elemType.Kind() != reflect.Ptr {
				panic(fmt.Errorf("field %s contains a non-pointer interface", propertyName))
			}
			fallthrough
		case reflect.Ptr:
			switch ptrKind := fieldValue.Type().Elem().Kind(); ptrKind {
			case reflect.Struct:
				if fieldValue.IsNil() && (propertyIsSet || field.Anonymous) {
					// Instantiate nil struct pointers
					// Set into origFieldValue in case it was an interface, in which case
					// fieldValue points to the unsettable pointer inside the interface
					fieldValue = reflect.New(fieldValue.Type().Elem())
					origFieldValue.Set(fieldValue)
				}
				fieldValue = fieldValue.Elem()
			case reflect.Bool, reflect.Int64, reflect.String:
				// Nothing
			default:
				panic(fmt.Errorf("field %s contains a pointer to %s", propertyName, ptrKind))
			}

		case reflect.Int, reflect.Uint:
			if !HasTag(field, "blueprint", "mutated") {
				panic(fmt.Errorf(`int field %s must be tagged blueprint:"mutated"`, propertyName))
			}

		default:
			panic(fmt.Errorf("unsupported kind for field %s: %s", propertyName, kind))
		}

		if field.Anonymous && isStruct(fieldValue.Type()) {
			ctx.unpackToStruct(namePrefix, fieldValue)
			continue
		}

		if !propertyIsSet {
			// This property wasn't specified.
			continue
		}

		packedProperty.used = true
		property := packedProperty.property

		if HasTag(field, "blueprint", "mutated") {
			if !ctx.addError(
				&UnpackError{
					fmt.Errorf("mutated field %s cannot be set in a Blueprint file", propertyName),
					property.ColonPos,
				}) {
				return
			}
			continue
		}

		if isStruct(fieldValue.Type()) {
			if property.Value.Eval().Type() != parser.MapType {
				ctx.addError(&UnpackError{
					fmt.Errorf("can't assign %s value to map property %q",
						property.Value.Type(), property.Name),
					property.Value.Pos(),
				})
				continue
			}
			ctx.unpackToStruct(propertyName, fieldValue)
			if len(ctx.errs) >= maxUnpackErrors {
				return
			}
		} else if isSlice(fieldValue.Type()) {
			if unpackedValue, ok := ctx.unpackToSlice(propertyName, property, fieldValue.Type()); ok {
				ExtendBasicType(fieldValue, unpackedValue, Append)
			}
			if len(ctx.errs) >= maxUnpackErrors {
				return
			}
		} else if fieldValue.Type().Kind() == reflect.Map {
			if unpackedValue, ok := ctx.unpackToMap(propertyName, property, fieldValue.Type()); ok {
				ExtendBasicType(fieldValue, unpackedValue, Append)
			}
			if len(ctx.errs) >= maxUnpackErrors {
				return
			}

		} else {
			unpackedValue, err := propertyToValue(fieldValue.Type(), property)
			if err != nil && !ctx.addError(err) {
				return
			}
			ExtendBasicType(fieldValue, unpackedValue, Append)
		}
	}
}

// unpackToMap unpacks given parser.property into a go map of type mapType
func (ctx *unpackContext) unpackToMap(mapName string, property *parser.Property, mapType reflect.Type) (reflect.Value, bool) {
	propValueAsMap, ok := property.Value.Eval().(*parser.Map)
	// Verify this property is a map
	if !ok {
		ctx.addError(&UnpackError{
			fmt.Errorf("can't assign %q value to map property %q", property.Value.Type(), property.Name),
			property.Value.Pos(),
		})
		return reflect.MakeMap(mapType), false
	}
	// And is not a struct
	if len(propValueAsMap.Properties) > 0 {
		ctx.addError(&UnpackError{
			fmt.Errorf("can't assign property to a map (%s) property %q", property.Value.Type(), property.Name),
			property.Value.Pos(),
		})
		return reflect.MakeMap(mapType), false
	}

	items := propValueAsMap.MapItems
	m := reflect.MakeMap(mapType)
	if len(items) == 0 {
		return m, true
	}
	keyConstructor := ctx.itemConstructor(items[0].Key.Type())
	keyType := mapType.Key()
	valueConstructor := ctx.itemConstructor(items[0].Value.Type())
	valueType := mapType.Elem()

	itemProperty := &parser.Property{NamePos: property.NamePos, ColonPos: property.ColonPos}
	for i, item := range items {
		itemProperty.Name = fmt.Sprintf("%s{key:%d}", mapName, i)
		itemProperty.Value = item.Key
		if packedProperty, ok := ctx.propertyMap[itemProperty.Name]; ok {
			packedProperty.used = true
		}
		keyValue, ok := itemValue(keyConstructor, itemProperty, keyType)
		if !ok {
			continue
		}
		itemProperty.Name = fmt.Sprintf("%s{value:%d}", mapName, i)
		itemProperty.Value = item.Value
		if packedProperty, ok := ctx.propertyMap[itemProperty.Name]; ok {
			packedProperty.used = true
		}
		value, ok := itemValue(valueConstructor, itemProperty, valueType)
		if ok {
			m.SetMapIndex(keyValue, value)
		}
	}

	return m, true
}

// unpackSlice creates a value of a given slice type from the property which should be a list
func (ctx *unpackContext) unpackToSlice(
	sliceName string, property *parser.Property, sliceType reflect.Type) (reflect.Value, bool) {
	propValueAsList, ok := property.Value.Eval().(*parser.List)
	if !ok {
		ctx.addError(&UnpackError{
			fmt.Errorf("can't assign %s value to list property %q",
				property.Value.Type(), property.Name),
			property.Value.Pos(),
		})
		return reflect.MakeSlice(sliceType, 0, 0), false
	}
	exprs := propValueAsList.Values
	value := reflect.MakeSlice(sliceType, 0, len(exprs))
	if len(exprs) == 0 {
		return value, true
	}

	itemConstructor := ctx.itemConstructor(exprs[0].Type())
	itemType := sliceType.Elem()

	itemProperty := &parser.Property{NamePos: property.NamePos, ColonPos: property.ColonPos}
	for i, expr := range exprs {
		itemProperty.Name = sliceName + "[" + strconv.Itoa(i) + "]"
		itemProperty.Value = expr
		if packedProperty, ok := ctx.propertyMap[itemProperty.Name]; ok {
			packedProperty.used = true
		}
		if itemValue, ok := itemValue(itemConstructor, itemProperty, itemType); ok {
			value = reflect.Append(value, itemValue)
		}
	}
	return value, true
}

// constructItem is a function to construct a reflect.Value from given parser.Property of reflect.Type
type constructItem func(*parser.Property, reflect.Type) (reflect.Value, bool)

// itemValue creates a new item of type t with value determined by f
func itemValue(f constructItem, property *parser.Property, t reflect.Type) (reflect.Value, bool) {
	isPtr := t.Kind() == reflect.Ptr
	if isPtr {
		t = t.Elem()
	}
	val, ok := f(property, t)
	if !ok {
		return val, ok
	}
	if isPtr {
		ptrValue := reflect.New(val.Type())
		ptrValue.Elem().Set(val)
		return ptrValue, true
	}
	return val, true
}

// itemConstructor returns a function  to construct an item of typ
func (ctx *unpackContext) itemConstructor(typ parser.Type) constructItem {
	// The function to construct an item value depends on the type of list elements.
	switch typ {
	case parser.BoolType, parser.StringType, parser.Int64Type:
		return func(property *parser.Property, t reflect.Type) (reflect.Value, bool) {
			value, err := propertyToValue(t, property)
			if err != nil {
				ctx.addError(err)
				return value, false
			}
			return value, true
		}
	case parser.ListType:
		return func(property *parser.Property, t reflect.Type) (reflect.Value, bool) {
			return ctx.unpackToSlice(property.Name, property, t)
		}
	case parser.MapType:
		return func(property *parser.Property, t reflect.Type) (reflect.Value, bool) {
			if t.Kind() == reflect.Map {
				return ctx.unpackToMap(property.Name, property, t)
			} else {
				itemValue := reflect.New(t).Elem()
				ctx.unpackToStruct(property.Name, itemValue)
				return itemValue, true
			}
		}
	case parser.NotEvaluatedType:
		return func(property *parser.Property, t reflect.Type) (reflect.Value, bool) {
			return reflect.New(t), false
		}
	default:
		panic(fmt.Errorf("bizarre property expression type: %v", typ))
	}
}

// propertyToValue creates a value of a given value type from the property.
func propertyToValue(typ reflect.Type, property *parser.Property) (reflect.Value, error) {
	var value reflect.Value
	var baseType reflect.Type
	isPtr := typ.Kind() == reflect.Ptr
	if isPtr {
		baseType = typ.Elem()
	} else {
		baseType = typ
	}

	switch kind := baseType.Kind(); kind {
	case reflect.Bool:
		b, ok := property.Value.Eval().(*parser.Bool)
		if !ok {
			return value, &UnpackError{
				fmt.Errorf("can't assign %s value to bool property %q",
					property.Value.Type(), property.Name),
				property.Value.Pos(),
			}
		}
		value = reflect.ValueOf(b.Value)

	case reflect.Int64:
		b, ok := property.Value.Eval().(*parser.Int64)
		if !ok {
			return value, &UnpackError{
				fmt.Errorf("can't assign %s value to int64 property %q",
					property.Value.Type(), property.Name),
				property.Value.Pos(),
			}
		}
		value = reflect.ValueOf(b.Value)

	case reflect.String:
		s, ok := property.Value.Eval().(*parser.String)
		if !ok {
			return value, &UnpackError{
				fmt.Errorf("can't assign %s value to string property %q",
					property.Value.Type(), property.Name),
				property.Value.Pos(),
			}
		}
		value = reflect.ValueOf(s.Value)

	default:
		return value, &UnpackError{
			fmt.Errorf("cannot assign %s value %s to %s property %s", property.Value.Type(), property.Value, kind, typ),
			property.NamePos}
	}

	if isPtr {
		ptrValue := reflect.New(value.Type())
		ptrValue.Elem().Set(value)
		return ptrValue, nil
	}
	return value, nil
}
