//go:build windows
// +build windows

/*
 * PressO Desktop - Go API Hub Engine
 * ====================================
 *
 * FILE: secure/dpapi_windows.go
 * RESPONSIBILITY: Windows DPAPI credential encryption
 *
 * ARCHITECTURAL ROLE:
 * - Encrypt credentials using Windows Data Protection API
 * - Credentials encrypted at rest, decrypted only in memory
 * - User-scope encryption (tied to Windows user account)
 *
 * SECURITY REQUIREMENTS (PROJECT_DOCUMENTATION.md Section 6.4):
 * - API keys stored in secure/credentials.enc
 * - Encrypted using Windows DPAPI
 * - Decrypted only in memory when needed
 * - NEVER logged or transmitted in plain text
 *
 * Reference: PROJECT_DOCUMENTATION.md Section 6.4
 */

package secure

import (
	"fmt"
	"syscall"
	"unsafe"
)

var (
	dllCrypt32  = syscall.NewLazyDLL("crypt32.dll")
	dllKernel32 = syscall.NewLazyDLL("kernel32.dll")

	procCryptProtectData   = dllCrypt32.NewProc("CryptProtectData")
	procCryptUnprotectData = dllCrypt32.NewProc("CryptUnprotectData")
	procLocalFree          = dllKernel32.NewProc("LocalFree")
)

// DATA_BLOB structure for DPAPI
type dataBlob struct {
	cbData uint32
	pbData *byte
}

// CRYPTPROTECT flags
const (
	CRYPTPROTECT_UI_FORBIDDEN = 0x1
	CRYPTPROTECT_LOCAL_MACHINE = 0x4
)

// Encrypt encrypts data using Windows DPAPI (user scope)
// The encrypted data can only be decrypted by the same Windows user
func Encrypt(plaintext []byte) ([]byte, error) {
	if len(plaintext) == 0 {
		return nil, fmt.Errorf("cannot encrypt empty data")
	}

	var inBlob dataBlob
	inBlob.cbData = uint32(len(plaintext))
	inBlob.pbData = &plaintext[0]

	var outBlob dataBlob

	// CryptProtectData with UI_FORBIDDEN flag (no prompts)
	ret, _, err := procCryptProtectData.Call(
		uintptr(unsafe.Pointer(&inBlob)),
		0, // szDataDescr (optional description)
		0, // pOptionalEntropy (additional entropy)
		0, // pvReserved
		0, // pPromptStruct
		uintptr(CRYPTPROTECT_UI_FORBIDDEN),
		uintptr(unsafe.Pointer(&outBlob)),
	)

	if ret == 0 {
		return nil, fmt.Errorf("CryptProtectData failed: %v", err)
	}

	// Copy output to Go slice
	defer procLocalFree.Call(uintptr(unsafe.Pointer(outBlob.pbData)))
	
	encrypted := make([]byte, outBlob.cbData)
	copy(encrypted, unsafe.Slice(outBlob.pbData, outBlob.cbData))

	return encrypted, nil
}

// Decrypt decrypts data using Windows DPAPI
// Only works for data encrypted by the same Windows user
func Decrypt(ciphertext []byte) ([]byte, error) {
	if len(ciphertext) == 0 {
		return nil, fmt.Errorf("cannot decrypt empty data")
	}

	var inBlob dataBlob
	inBlob.cbData = uint32(len(ciphertext))
	inBlob.pbData = &ciphertext[0]

	var outBlob dataBlob

	// CryptUnprotectData with UI_FORBIDDEN flag
	ret, _, err := procCryptUnprotectData.Call(
		uintptr(unsafe.Pointer(&inBlob)),
		0, // ppszDataDescr
		0, // pOptionalEntropy
		0, // pvReserved
		0, // pPromptStruct
		uintptr(CRYPTPROTECT_UI_FORBIDDEN),
		uintptr(unsafe.Pointer(&outBlob)),
	)

	if ret == 0 {
		return nil, fmt.Errorf("CryptUnprotectData failed: %v", err)
	}

	// Copy output to Go slice
	defer procLocalFree.Call(uintptr(unsafe.Pointer(outBlob.pbData)))
	
	decrypted := make([]byte, outBlob.cbData)
	copy(decrypted, unsafe.Slice(outBlob.pbData, outBlob.cbData))

	return decrypted, nil
}

// IsAvailable checks if DPAPI is available on this system
func IsAvailable() bool {
	err := dllCrypt32.Load()
	return err == nil
}

