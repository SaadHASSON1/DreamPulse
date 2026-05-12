import SwiftUI
import HealthKit

// الواجهة الرئيسية لتطبيق DreamPulse على ساعة أبل
struct ContentView: View {
    @State private var isTracking = false
    @State private var heartRate = 0
    let healthStore = HKHealthStore()
    
    var body: some View {
        VStack {
            Text("DreamPulse")
                .font(.headline)
                .foregroundColor(.blue)
            
            Spacer()
            
            if isTracking {
                Image(systemName: "moon.stars.fill")
                    .font(.largeTitle)
                    .foregroundColor(.yellow)
                Text("جاري تتبع النوم...")
                    .foregroundColor(.green)
                    .padding(.top, 4)
                
                Text("النبض: \(heartRate) BPM")
                    .font(.footnote)
                    .foregroundColor(.gray)
            } else {
                Image(systemName: "bed.double.fill")
                    .font(.largeTitle)
                    .foregroundColor(.gray)
                Text("جاهز للنوم؟")
                    .padding(.top, 4)
            }
            
            Spacer()
            
            Button(action: toggleTracking) {
                Text(isTracking ? "إيقاف التتبع" : "بدء التتبع")
                    .fontWeight(.bold)
            }
            .tint(isTracking ? .red : .blue)
        }
        .onAppear(perform: requestAuthorization)
    }
    
    // طلب صلاحيات الوصول للبيانات الصحية (نبض القلب والنوم)
    func requestAuthorization() {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        
        let typesToShare: Set = [
            HKObjectType.categoryType(forIdentifier: .sleepAnalysis)!
        ]
        let typesToRead: Set = [
            HKObjectType.categoryType(forIdentifier: .sleepAnalysis)!,
            HKObjectType.quantityType(forIdentifier: .heartRate)!
        ]
        
        healthStore.requestAuthorization(toShare: typesToShare, read: typesToRead) { success, error in
            if success {
                print("تم أخذ الصلاحيات بنجاح")
            }
        }
    }
    
    func toggleTracking() {
        isTracking.toggle()
        if isTracking {
            // هنا يتم تشغيل جلسة تتبع النوم (مثلاً باستخدام Extended Runtime Sessions)
        } else {
            // إيقاف الجلسة
        }
    }
}

// نقطة البداية للتطبيق
@main
struct DreamPulseWatchApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
