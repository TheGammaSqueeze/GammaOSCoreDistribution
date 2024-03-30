extern crate proc_macro;

use quote::quote;

use std::fs::File;
use std::io::Write;
use std::path::Path;

use syn::parse::Parser;
use syn::punctuated::Punctuated;
use syn::token::Comma;
use syn::{Expr, FnArg, ItemTrait, Meta, Pat, TraitItem};

use crate::proc_macro::TokenStream;

const OUTPUT_DEBUG: bool = false;

fn debug_output_to_file(gen: &proc_macro2::TokenStream, filename: String) {
    if !OUTPUT_DEBUG {
        return;
    }

    let filepath = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap())
        .join(filename)
        .to_str()
        .unwrap()
        .to_string();

    let path = Path::new(&filepath);
    let mut file = File::create(&path).unwrap();
    file.write_all(gen.to_string().as_bytes()).unwrap();
}

/// Associates a function with a btif callback message.
#[proc_macro_attribute]
pub fn btif_callback(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let ori_item: proc_macro2::TokenStream = item.clone().into();
    let gen = quote! {
        #ori_item
    };
    gen.into()
}

/// Generates a dispatcher from a message to a function.
#[proc_macro_attribute]
pub fn btif_callbacks_dispatcher(attr: TokenStream, item: TokenStream) -> TokenStream {
    let args = Punctuated::<Expr, Comma>::parse_separated_nonempty.parse(attr.clone()).unwrap();

    let struct_ident = if let Expr::Path(p) = &args[0] {
        p.path.get_ident().unwrap()
    } else {
        panic!("struct name must be specified");
    };

    let fn_ident = if let Expr::Path(p) = &args[1] {
        p.path.get_ident().unwrap()
    } else {
        panic!("function name must be specified");
    };

    let callbacks_struct_ident = if let Expr::Path(p) = &args[2] {
        p.path.get_ident().unwrap()
    } else {
        panic!("callbacks struct ident must be specified");
    };

    let mut dispatch_arms = quote! {};

    let ast: ItemTrait = syn::parse(item.clone()).unwrap();

    let mut fn_names = quote! {};
    for attr in ast.items {
        if let TraitItem::Method(m) = attr {
            if m.attrs.len() != 1 {
                continue;
            }

            let attr = &m.attrs[0];
            if !attr.path.get_ident().unwrap().to_string().eq("btif_callback") {
                continue;
            }

            let attr_args = attr.parse_meta().unwrap();
            let btif_callback = if let Meta::List(meta_list) = attr_args {
                Some(meta_list.nested[0].clone())
            } else {
                None
            };

            if btif_callback.is_none() {
                continue;
            }

            let mut arg_names = quote! {};
            for input in m.sig.inputs {
                if let FnArg::Typed(t) = input {
                    if let Pat::Ident(i) = *t.pat {
                        let attr_name = i.ident;
                        arg_names = quote! { #arg_names #attr_name, };
                    }
                }
            }
            let method_ident = m.sig.ident;

            fn_names = quote! {
                #fn_names
                #method_ident,
            };

            dispatch_arms = quote! {
                #dispatch_arms
                #callbacks_struct_ident::#btif_callback(#arg_names) => {
                    self.#method_ident(#arg_names);
                }
            };
        }
    }

    let ori_item = proc_macro2::TokenStream::from(item.clone());

    let gen = quote! {
        #ori_item
        impl #struct_ident {
            pub(crate) fn #fn_ident(&mut self, cb: #callbacks_struct_ident) {
                match cb {
                    #dispatch_arms

                    _ => println!("Unhandled callback arm {:?}", cb),
                }
            }
        }
    };

    // TODO: Have a simple framework to turn on/off macro-generated code debug.
    debug_output_to_file(&gen, format!("out-{}.rs", fn_ident.to_string()));

    gen.into()
}
