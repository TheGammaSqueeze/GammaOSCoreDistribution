#[test]
fn multiple_init() {
    let first_init = logger::init(Default::default());
    let second_init = logger::init(Default::default());

    assert!(first_init);
    assert!(!second_init);
}