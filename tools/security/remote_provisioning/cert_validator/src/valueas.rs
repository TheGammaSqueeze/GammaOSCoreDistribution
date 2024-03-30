//! Defines the interface for converting a given (cbor decoded)
//! type into convenient data types.

use anyhow::{anyhow, ensure, Result};
use chrono::NaiveDate;
use ciborium::value::Value;
use thiserror::Error;

#[derive(Error, Debug)]
pub(crate) enum ValueAsError {
    #[error("Wrong type")]
    WrongType(),
    #[error("Wrong length: expected {0} got {1}")]
    WrongLength(usize, usize),
    #[error("Key absent")]
    KeyAbsent(),
    #[error("Not the same: expected {0:?} got {1:?}")]
    NotSameError(Value, Value),
    #[error("Integer too big for i64")]
    IntegerTooBigError(),
}
pub(crate) trait ValueAs
where
    Self: Sized,
{
    fn as_i64(&self) -> Result<i64, ValueAsError>;
    fn as_u64(&self) -> Result<u64, ValueAsError>;
    fn as_bytes(&self) -> Result<&[u8], ValueAsError>;
    fn as_array(&self) -> Result<&[Self], ValueAsError>;
    fn as_string(&self) -> Result<String, ValueAsError>;
    fn as_date(&self) -> Result<NaiveDate, ValueAsError>;
    fn as_array_of_len(&self, len: usize) -> Result<&[Self], ValueAsError>;
    fn as_map(&self) -> Result<&[(Self, Self)], ValueAsError>;
    fn map_lookup<T: Into<Self>>(&self, key: T) -> Result<&Self, ValueAsError>;
    fn must_equal<T: Into<Self>>(&self, other: T) -> Result<(), ValueAsError>;
    fn check_date_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()>;
    fn check_string_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()>;
    fn check_bytes_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()>;
    fn check_arr_val_if_key_in_map<T: Into<Self>>(&self, key: T, arr: &[&str]) -> Result<()>;
    fn check_arr_val_for_key_in_map<T: Into<Self>>(&self, key: T, arr: &[&str]) -> Result<()>;
}

impl ValueAs for Value {
    fn as_i64(&self) -> Result<i64, ValueAsError> {
        if let Value::Integer(i) = self {
            let i = i128::from(*i);
            Ok(i.try_into().map_err(|_| ValueAsError::IntegerTooBigError())?)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_u64(&self) -> Result<u64, ValueAsError> {
        if let Value::Integer(i) = self {
            let i = i128::from(*i);
            Ok(i.try_into().map_err(|_| ValueAsError::IntegerTooBigError())?)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_bytes(&self) -> Result<&[u8], ValueAsError> {
        if let Value::Bytes(b) = self {
            Ok(b)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_array(&self) -> Result<&[Self], ValueAsError> {
        if let Value::Array(a) = self {
            Ok(a)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_string(&self) -> Result<String, ValueAsError> {
        if let Value::Text(s) = self {
            Ok(s.to_string())
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_date(&self) -> Result<NaiveDate, ValueAsError> {
        let date = NaiveDate::parse_from_str(&self.as_u64()?.to_string(), "%Y%m%d");
        if let Ok(date) = date {
            Ok(date)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn as_array_of_len(&self, len: usize) -> Result<&[Self], ValueAsError> {
        let a = ValueAs::as_array(self)?;

        if a.len() == len {
            Ok(a)
        } else {
            Err(ValueAsError::WrongLength(len, a.len()))
        }
    }

    fn as_map(&self) -> Result<&[(Self, Self)], ValueAsError> {
        if let Value::Map(a) = self {
            Ok(a)
        } else {
            Err(ValueAsError::WrongType())
        }
    }

    fn map_lookup<T: Into<Self>>(&self, key: T) -> Result<&Self, ValueAsError> {
        let a = ValueAs::as_map(self)?;
        let key: Value = key.into();
        for (k, v) in a {
            if k == &key {
                return Ok(v);
            }
        }
        Err(ValueAsError::KeyAbsent())
    }

    fn must_equal<T: Into<Self>>(&self, other: T) -> Result<(), ValueAsError> {
        let other: Value = other.into();
        if *self == other {
            Ok(())
        } else {
            Err(ValueAsError::NotSameError(other, self.clone()))
        }
    }

    fn check_date_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()> {
        let result = self.map_lookup(key);
        if result.is_ok() {
            result?.as_date()?;
        }
        Ok(())
    }

    fn check_string_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()> {
        let result = self.map_lookup(key);
        if result.is_ok() {
            result?.as_string()?;
        }
        Ok(())
    }

    fn check_arr_val_if_key_in_map<T: Into<Self>>(&self, key: T, arr: &[&str]) -> Result<()> {
        let result = self.map_lookup(key);
        if result.is_ok() {
            let result = result?.as_string()?;
            ensure!(arr.iter().any(|&x| x.eq(&result)));
        }
        Ok(())
    }

    fn check_bytes_val_if_key_in_map<T: Into<Self>>(&self, key: T) -> Result<()> {
        let result = self.map_lookup(key);
        if result.is_ok() {
            result?.as_bytes().ok_or_else(|| anyhow!("map value not bytes"))?;
        }
        Ok(())
    }

    fn check_arr_val_for_key_in_map<T: Into<Self>>(&self, key: T, arr: &[&str]) -> Result<()> {
        let result = self.map_lookup(key)?.as_string()?;
        ensure!(arr.iter().any(|&x| x.eq(&result)));
        Ok(())
    }
}
