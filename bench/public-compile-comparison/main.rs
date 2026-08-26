#![no_std]

#[unsafe(no_mangle)]
pub extern "C" fn main() -> i64 {
    40 + 2
}

#[panic_handler]
fn panic(_info: &core::panic::PanicInfo<'_>) -> ! {
    loop {}
}
