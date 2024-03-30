use core::any::Any;

use dbus_macros::{dbus_propmap, generate_dbus_arg};

use dbus::arg::{Arg, ArgType, IterAppend, RefArg};
use dbus::Signature;

generate_dbus_arg!();

#[derive(Debug, Default, Clone, PartialEq)]
struct OtherStruct {
    address: String,
}

#[dbus_propmap(OtherStruct)]
struct OtherStructDBus {
    address: String,
}

#[derive(Debug, Default, Clone, PartialEq)]
struct SomeStruct {
    name: String,
    number: i32,
    other_struct: OtherStruct,
    bytes: Vec<u8>,
    nested: Vec<Vec<String>>,
    recursive: Vec<SomeStruct>,
}

#[dbus_propmap(SomeStruct)]
struct SomeStructDBus {
    name: String,
    number: i32,
    other_struct: OtherStruct,
    bytes: Vec<u8>,
    nested: Vec<Vec<String>>,
    recursive: Vec<SomeStruct>,
}

// Pretends to be a D-Bus dictionary.
#[derive(Debug)]
struct FakeDictionary {
    items: Vec<(String, Box<dyn RefArg>)>,
}

impl RefArg for FakeDictionary {
    fn arg_type(&self) -> ArgType {
        todo!()
    }
    fn signature(&self) -> dbus::Signature<'static> {
        todo!()
    }
    fn append(&self, _: &mut IterAppend<'_>) {
        todo!()
    }
    fn as_any(&self) -> &(dyn Any + 'static) {
        todo!()
    }
    fn as_any_mut(&mut self) -> &mut (dyn Any + 'static) {
        todo!()
    }
    fn box_clone(&self) -> Box<dyn RefArg + 'static> {
        Box::new(FakeDictionary {
            items: self.items.iter().map(|(k, v)| (k.clone(), v.box_clone())).collect(),
        })
    }

    fn as_iter<'b>(&'b self) -> Option<Box<dyn Iterator<Item = &'b dyn RefArg> + 'b>> {
        Some(Box::new(
            self.items
                .iter()
                .flat_map(|(k, v)| vec![k as &dyn RefArg, v as &dyn RefArg].into_iter()),
        ))
    }
}

impl Arg for FakeDictionary {
    const ARG_TYPE: ArgType = ArgType::Array;
    fn signature() -> dbus::Signature<'static> {
        Signature::from("a{sv}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dbus_propmap_error() {
        let data_dbus = String::from("some data");
        let result = <dbus::arg::PropMap as RefArgToRust>::ref_arg_to_rust(
            &data_dbus,
            String::from("Some Variable"),
        );
        assert!(result.is_err());
        assert_eq!("Some Variable is not iterable", result.unwrap_err().to_string());
    }

    #[test]
    fn test_dbus_propmap_success() {
        let data_dbus = FakeDictionary {
            items: vec![
                (String::from("name"), Box::new(String::from("foo"))),
                (String::from("number"), Box::new(100)),
                (
                    String::from("other_struct"),
                    Box::new(FakeDictionary {
                        items: vec![(
                            String::from("address"),
                            Box::new(String::from("aa:bb:cc:dd:ee:ff")),
                        )],
                    }),
                ),
                (String::from("bytes"), Box::new(vec![1 as u8, 2, 3])),
                (
                    String::from("nested"),
                    Box::new(vec![
                        vec![
                            String::from("string a"),
                            String::from("string b"),
                            String::from("string c"),
                        ],
                        vec![String::from("string 1"), String::from("string 2")],
                    ]),
                ),
                (
                    String::from("recursive"),
                    Box::new(vec![FakeDictionary {
                        items: vec![
                            (String::from("name"), Box::new(String::from("bar"))),
                            (String::from("number"), Box::new(200)),
                            (
                                String::from("other_struct"),
                                Box::new(FakeDictionary {
                                    items: vec![(
                                        String::from("address"),
                                        Box::new(String::from("xx")),
                                    )],
                                }),
                            ),
                            (String::from("bytes"), Box::new(Vec::<u8>::new())),
                            (String::from("nested"), Box::new(Vec::<Vec<u8>>::new())),
                            (String::from("recursive"), Box::new(Vec::<FakeDictionary>::new())),
                        ],
                    }]),
                ),
            ],
        };
        let result = <dbus::arg::PropMap as RefArgToRust>::ref_arg_to_rust(
            &data_dbus,
            String::from("Some Variable"),
        );
        assert!(result.is_ok());
        let result = result.unwrap();
        let result_struct = <SomeStruct as DBusArg>::from_dbus(result, None, None, None).unwrap();
        let expected_struct = SomeStruct {
            name: String::from("foo"),
            number: 100,
            other_struct: OtherStruct { address: String::from("aa:bb:cc:dd:ee:ff") },
            bytes: vec![1, 2, 3],
            nested: vec![
                vec![String::from("string a"), String::from("string b"), String::from("string c")],
                vec![String::from("string 1"), String::from("string 2")],
            ],
            recursive: vec![SomeStruct {
                name: String::from("bar"),
                number: 200,
                other_struct: OtherStruct { address: String::from("xx") },
                bytes: vec![],
                nested: vec![],
                recursive: vec![],
            }],
        };
        assert_eq!(expected_struct, result_struct);
    }
}
